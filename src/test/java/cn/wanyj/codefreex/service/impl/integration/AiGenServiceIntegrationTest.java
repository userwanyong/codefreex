package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.AiGenService;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.testutil.UserContextTestHelper;
import com.mybatisflex.core.query.QueryWrapper;
import org.mockito.ArgumentCaptor;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier;

import java.util.List;

import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestConfig.class)
class AiGenServiceIntegrationTest {

    @Autowired
    private AiGenService aiGenService;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    @Autowired
    private AppMapper appMapper;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ApplicationContext applicationContext;

    private StreamingChatModel streamingChatModel;

    @BeforeEach
    void setUp() {
        streamingChatModel = applicationContext.getBean(StreamingChatModel.class);
        reset(streamingChatModel);
    }

    @MockitoBean
    private ChatModel reviewChatModel;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private final Long appId = 1001L;
    private final Long userId = 2001L;

    @SuppressWarnings("unchecked")
    private void setupRedisMock() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private void mockStreamingSuccess(String htmlContent) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse(htmlContent);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(htmlContent))
                    .build());
            return null;
        }).when(streamingChatModel).chat(any(List.class), any(StreamingChatResponseHandler.class));
    }

    private void mockStreamingError(String errorMsg) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException(errorMsg));
            return null;
        }).when(streamingChatModel).chat(any(List.class), any(StreamingChatResponseHandler.class));
    }

    // ========== 验证项 1: 单轮对话完整流程 ==========

    @Test
    void chatToGenCode_fullFlow_savesBothMessages() {
        setupRedisMock();
        String htmlCode = "<!DOCTYPE html><html><body>Hello</body></html>";
        mockStreamingSuccess(htmlCode);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "build a hello page");

            StepVerifier.create(flux)
                    .expectNextCount(2) // ai_r + done
                    .verifyComplete();

            // Verify both messages in DB
            QueryWrapper query = QueryWrapper.create()
                    .where(CHAT_HISTORY.APP_ID.eq(appId))
                    .and(CHAT_HISTORY.USER_ID.eq(userId))
                    .orderBy(CHAT_HISTORY.CREATE_TIME.asc());
            List<ChatHistory> records = chatHistoryMapper.selectListByQuery(query);

            assertThat(records).hasSize(2);
            assertThat(records.get(0).getMessageType()).isEqualTo("user");
            assertThat(records.get(0).getMessage()).isEqualTo("build a hello page");
            assertThat(records.get(1).getMessageType()).isEqualTo("ai");
            assertThat(records.get(1).getParentId()).isEqualTo(records.get(0).getId());
        }
    }

    @Test
    void chatToGenCode_success_statusBecomesGenerated() {
        setupRedisMock();
        mockStreamingSuccess("<!DOCTYPE html><html></html>");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            App app = appMapper.selectOneById(appId);
            assertThat(app.getStatus()).isEqualTo("generated");
        }
    }

    // ========== 验证项 5: 异常中断 ==========

    @Test
    void chatToGenCode_streamError_errorRecordInDb() {
        setupRedisMock();
        mockStreamingError("Connection timeout");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "build something");

            StepVerifier.create(flux)
                    .expectNextCount(1) // error event
                    .verifyComplete();

            // Verify error message saved
            QueryWrapper query = QueryWrapper.create()
                    .where(CHAT_HISTORY.APP_ID.eq(appId))
                    .and(CHAT_HISTORY.USER_ID.eq(userId))
                    .orderBy(CHAT_HISTORY.CREATE_TIME.asc());
            List<ChatHistory> records = chatHistoryMapper.selectListByQuery(query);

            assertThat(records).hasSize(2);
            assertThat(records.get(0).getMessageType()).isEqualTo("user");
            assertThat(records.get(1).getMessageType()).isEqualTo("ai");
            assertThat(records.get(1).getMessage()).contains("Connection timeout");
            assertThat(records.get(1).getParentId()).isEqualTo(records.get(0).getId());
        }
    }

    @Test
    void chatToGenCode_streamError_statusRevertsToDraft() {
        setupRedisMock();
        mockStreamingError("fail");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(1).verifyComplete();

            App app = appMapper.selectOneById(appId);
            assertThat(app.getStatus()).isEqualTo("draft");
        }
    }

    // ========== 验证项 2: 多轮对话 ==========

    @Test
    void chatToGenCode_multiTurn_memoryContainsPriorContext() {
        setupRedisMock();
        mockStreamingSuccess("<html>response</html>");

        // Seed prior conversation
        chatHistoryService.saveUserMessage(appId, userId, "first question");
        chatHistoryService.saveAiMessage(appId, userId, "first answer");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "follow up question");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            // Verify the streaming model received messages with prior context
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(streamingChatModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));

            List<ChatMessage> sentMessages = captor.getValue();
            // Should contain prior messages + new message
            assertThat(sentMessages.size()).isGreaterThanOrEqualTo(3);
        }
    }
}
