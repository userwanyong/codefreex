package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.dto.response.PromptReviewResult;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import cn.wanyj.codefreex.testutil.TestDataFactory;
import cn.wanyj.codefreex.testutil.UserContextTestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGenServiceImplTest {

    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private ChatModel reviewChatModel;
    @Mock
    private AiConfig.PromptLoader promptLoader;
    @Mock
    private CodePersistStrategy htmlSingleFileStrategy;
    @Mock
    private AppMapper appMapper;
    @Mock
    private AppService appService;
    @Mock
    private ChatHistoryService chatHistoryService;
    @Mock
    private ChatMemoryService chatMemoryService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiGenServiceImpl aiGenService;

    private final Long appId = 1001L;
    private final Long userId = 2001L;

    private App mockApp() {
        App app = TestDataFactory.createApp(appId, userId, "draft");
        when(appMapper.selectOneById(appId)).thenReturn(app);
        return app;
    }

    private void mockPromptLoader() {
        lenient().when(promptLoader.load("html_single.txt")).thenReturn("You are an HTML generator.");
        lenient().when(promptLoader.load("prompt_review.txt")).thenReturn("Review this prompt: ");
        lenient().when(promptLoader.load("prompt_optimize.txt")).thenReturn("Optimize this prompt: ");
    }

    @SuppressWarnings("unchecked")
    private void mockStreamingModel(String... partialTokens) {
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        when(applicationContext.getBean(StreamingChatModel.class)).thenReturn(streamingModel);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            StringBuilder full = new StringBuilder();
            for (String token : partialTokens) {
                full.append(token);
                handler.onPartialResponse(token);
            }
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(full.toString()))
                    .build());
            return null;
        }).when(streamingModel).chat(any(List.class), any(StreamingChatResponseHandler.class));
    }

    private void mockChatMemory() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemoryService.getChatMemory(eq(appId), eq(userId), anyString())).thenReturn(chatMemory);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from("test"));
        when(chatMemory.messages()).thenReturn(messages);
    }

    // ========== chatToGenCode — 验证项 1: 消息保存 ==========

    @Test
    void chatToGenCode_savesUserAndAiMessages() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();
        mockStreamingModel("Hello ", "World");

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");

            StepVerifier.create(flux)
                    .expectNextCount(3) // 2 ai_r + done
                    .verifyComplete();

            verify(chatHistoryService).saveUserMessage(appId, userId, "test");
            verify(chatHistoryService).saveAiMessage(eq(appId), eq(userId), contains("Hello World"), eq(1L));
        }
    }

    // ========== chatToGenCode — 验证项 2: 多轮对话上下文 ==========

    @Test
    void chatToGenCode_multiTurn_usesMemoryContext() {
        mockApp();
        mockPromptLoader();
        mockStreamingModel("response");

        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemoryService.getChatMemory(eq(appId), eq(userId), anyString())).thenReturn(chatMemory);
        // Simulate prior conversation in memory
        List<ChatMessage> priorMessages = new ArrayList<>();
        priorMessages.add(UserMessage.from("first message"));
        priorMessages.add(AiMessage.from("first response"));
        priorMessages.add(UserMessage.from("second message"));
        when(chatMemory.messages()).thenReturn(priorMessages);

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "second message");
        when(chatHistoryService.saveUserMessage(appId, userId, "second message")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "second message");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            // Verify streaming model received the prior messages (including history)
            ArgumentCaptor<List<ChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
            verify(applicationContext.getBean(StreamingChatModel.class))
                    .chat(messagesCaptor.capture(), any(StreamingChatResponseHandler.class));
            assertThat(messagesCaptor.getValue()).hasSize(3);
        }
    }

    // ========== chatToGenCode — 验证项 5: 异常中断 ==========

    @Test
    void chatToGenCode_errorInterrupt_savesErrorToDb() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();

        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        when(applicationContext.getBean(StreamingChatModel.class)).thenReturn(streamingModel);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("Connection lost"));
            return null;
        }).when(streamingModel).chat(any(List.class), any(StreamingChatResponseHandler.class));

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux)
                    .expectNextCount(1) // error event
                    .verifyComplete();

            // Verify error message saved to DB
            verify(chatHistoryService).saveAiMessage(eq(appId), eq(userId), contains("Connection lost"), eq(1L));
        }
    }

    @Test
    void chatToGenCode_errorInterrupt_revertsStatusToDraft() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();

        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        when(applicationContext.getBean(StreamingChatModel.class)).thenReturn(streamingModel);

        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("fail"));
            return null;
        }).when(streamingModel).chat(any(List.class), any(StreamingChatResponseHandler.class));

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(1).verifyComplete();

            // Should set GENERATING then revert to DRAFT
            verify(appService).updateAppStatus(appId, "generating");
            verify(appService).updateAppStatus(appId, "draft");
        }
    }

    // ========== chatToGenCode — 状态流转 ==========

    @Test
    void chatToGenCode_statusTransitions_draftGeneratingGenerated() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();
        mockStreamingModel("<!DOCTYPE html><html></html>");

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            var inOrder = inOrder(appService);
            inOrder.verify(appService).updateAppStatus(appId, "generating");
            inOrder.verify(appService).updateAppStatus(appId, "generated");
        }
    }

    // ========== chatToGenCode — 权限校验 ==========

    @Test
    void chatToGenCode_appNotFound_throwsException() {
        when(appMapper.selectOneById(appId)).thenReturn(null);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            assertThatThrownBy(() -> aiGenService.chatToGenCode(appId, "test"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ResponseCode.NOT_FOUND_ERROR.getCode());
        }
    }

    @Test
    void chatToGenCode_wrongOwner_throwsException() {
        App app = TestDataFactory.createApp(appId, 9999L, "draft");
        when(appMapper.selectOneById(appId)).thenReturn(app);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            assertThatThrownBy(() -> aiGenService.chatToGenCode(appId, "test"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ResponseCode.NO_AUTH_ERROR.getCode());
        }
    }

    // ========== extractHtmlCode (indirect) ==========

    @Test
    void chatToGenCode_markdownCodeBlock_extractsHtml() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();
        String htmlCode = "<div>Hello</div>";
        mockStreamingModel("```html\n" + htmlCode + "\n```");

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            verify(htmlSingleFileStrategy).persist(anyString(), eq(htmlCode));
        }
    }

    @Test
    void chatToGenCode_doctypeFallback_extractsHtml() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();
        String fullHtml = "<!DOCTYPE html><html><body>Hello</body></html>";
        mockStreamingModel(fullHtml);

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            verify(htmlSingleFileStrategy).persist(anyString(), eq(fullHtml));
        }
    }

    @Test
    void chatToGenCode_noHtml_savesFullText() {
        mockApp();
        mockPromptLoader();
        mockChatMemory();
        String plainText = "This is just a plain text response.";
        mockStreamingModel(plainText);

        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "test");
        when(chatHistoryService.saveUserMessage(appId, userId, "test")).thenReturn(userHistory);
        when(htmlSingleFileStrategy.persist(anyString(), anyString())).thenReturn("/tmp/test");

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var flux = aiGenService.chatToGenCode(appId, "test");
            StepVerifier.create(flux).expectNextCount(2).verifyComplete();

            // No HTML detected, persist full text
            verify(htmlSingleFileStrategy).persist(anyString(), eq(plainText));
        }
    }

    // ========== reviewPrompt ==========

    @Test
    void reviewPrompt_validJson_parsesResult() {
        mockPromptLoader();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"safe\":true,\"route\":\"html\",\"reason\":\"ok\"}"))
                .build();
        when(reviewChatModel.chat(any(dev.langchain4j.data.message.UserMessage.class))).thenReturn(response);

        PromptReviewResult result = aiGenService.reviewPrompt("build a website");

        assertThat(result.isSafe()).isTrue();
        assertThat(result.getRouteSuggestion()).isEqualTo("html");
    }

    @Test
    void reviewPrompt_malformedJson_returnsSafeDefault() {
        mockPromptLoader();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("this is not json at all"))
                .build();
        when(reviewChatModel.chat(any(dev.langchain4j.data.message.UserMessage.class))).thenReturn(response);

        PromptReviewResult result = aiGenService.reviewPrompt("test");

        assertThat(result.isSafe()).isTrue();
        assertThat(result.getRouteSuggestion()).isEqualTo("html");
    }

    @Test
    void reviewPrompt_aiThrows_returnsSafeDefault() {
        mockPromptLoader();
        when(reviewChatModel.chat(any(dev.langchain4j.data.message.UserMessage.class)))
                .thenThrow(new RuntimeException("API error"));

        PromptReviewResult result = aiGenService.reviewPrompt("test");

        assertThat(result.isSafe()).isTrue();
    }

    // ========== optimizePrompt ==========

    @Test
    void optimizePrompt_success_returnsOptimized() {
        mockPromptLoader();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("Optimized: build a beautiful website"))
                .build();
        when(reviewChatModel.chat(any(dev.langchain4j.data.message.UserMessage.class))).thenReturn(response);

        String result = aiGenService.optimizePrompt("build a website");

        assertThat(result).isEqualTo("Optimized: build a beautiful website");
    }

    @Test
    void optimizePrompt_failure_returnsOriginal() {
        mockPromptLoader();
        when(reviewChatModel.chat(any(dev.langchain4j.data.message.UserMessage.class)))
                .thenThrow(new RuntimeException("API error"));

        String result = aiGenService.optimizePrompt("build a website");

        assertThat(result).isEqualTo("build a website");
    }
}
