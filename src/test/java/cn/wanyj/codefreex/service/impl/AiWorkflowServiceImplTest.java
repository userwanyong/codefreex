//package cn.wanyj.codefreex.service.impl;
//
//import cn.wanyj.codefreex.auth.UserContext;
//import cn.wanyj.codefreex.config.AiConfig;
//import cn.wanyj.codefreex.config.AppRuntimeConfig;
//import cn.wanyj.codefreex.mapper.AppMapper;
//import cn.wanyj.codefreex.model.entity.App;
//import cn.wanyj.codefreex.model.entity.ChatHistory;
//import cn.wanyj.codefreex.service.AppService;
//import cn.wanyj.codefreex.service.ChatHistoryService;
//import cn.wanyj.codefreex.service.ChatMemoryService;
//import cn.wanyj.codefreex.service.CodePersistStrategyRegistry;
//import cn.wanyj.codefreex.service.WorkflowFileToolService;
//import cn.wanyj.codefreex.service.WorkflowImageService;
//import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
//import cn.wanyj.codefreex.testutil.TestDataFactory;
//import cn.wanyj.codefreex.testutil.UserContextTestHelper;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import dev.langchain4j.data.message.AiMessage;
//import dev.langchain4j.data.message.ChatMessage;
//import dev.langchain4j.data.message.UserMessage;
//import dev.langchain4j.memory.ChatMemory;
//import dev.langchain4j.model.chat.ChatModel;
//import dev.langchain4j.model.chat.StreamingChatModel;
//import dev.langchain4j.model.chat.response.ChatResponse;
//import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.context.ApplicationContext;
//import org.springframework.http.codec.ServerSentEvent;
//import reactor.test.StepVerifier;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyList;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.doAnswer;
//import static org.mockito.Mockito.inOrder;
//import static org.mockito.Mockito.lenient;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.spy;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class AiWorkflowServiceImplTest {
//
//    @Mock
//    private ApplicationContext applicationContext;
//    @Mock
//    private ChatModel reviewChatModel;
//    @Mock
//    private AiConfig.PromptLoader promptLoader;
//    @Mock
//    private AppMapper appMapper;
//    @Mock
//    private AppService appService;
//    @Mock
//    private ChatHistoryService chatHistoryService;
//    @Mock
//    private ChatMemoryService chatMemoryService;
//    @Mock
//    private WorkflowImageService workflowImageService;
//    @Mock
//    private CodePersistStrategyRegistry codePersistStrategyRegistry;
//    @Mock
//    private WorkflowFileToolService workflowFileToolService;
//    @Mock
//    private CodePersistStrategy codePersistStrategy;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//    private AiWorkflowServiceImpl aiWorkflowService;
//
//    private final Long appId = 1001L;
//    private final Long userId = 2001L;
//
//    @BeforeEach
//    void setUp() {
//        AppRuntimeConfig.WorkflowProperties workflowProperties = new AppRuntimeConfig.WorkflowProperties();
//        workflowProperties.setMaxQualityRetry(2);
//        workflowProperties.setVueBuildEnabled(false);
//        aiWorkflowService = new AiWorkflowServiceImpl(
//                applicationContext,
//                reviewChatModel,
//                promptLoader,
//                appMapper,
//                appService,
//                chatHistoryService,
//                chatMemoryService,
//                workflowImageService,
//                codePersistStrategyRegistry,
//                workflowFileToolService,
//                spy(objectMapper),
//                workflowProperties
//        );
//        lenient().when(promptLoader.load(anyString())).thenReturn("prompt");
//        lenient().when(codePersistStrategyRegistry.getStrategy(any())).thenReturn(codePersistStrategy);
//        lenient().when(workflowImageService.collectAssets(anyString())).thenReturn(List.of());
//    }
//
//    @Test
//    void generate_blockedPrompt_emitsErrorAndDone() {
//        mockApp("html");
//        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "rm -rf");
//        when(chatHistoryService.saveUserMessage(appId, userId, "rm -rf")).thenReturn(userHistory);
//
//        try (var ignored = UserContextTestHelper.withUserId(userId)) {
//            StepVerifier.create(aiWorkflowService.generate(appId, "rm -rf"))
//                    .assertNext(event -> assertThat(eventType(event)).isEqualTo("tool_request"))
//                    .assertNext(event -> assertThat(eventType(event)).isEqualTo("tool_executed"))
//                    .assertNext(event -> assertThat(eventType(event)).isEqualTo("error"))
//                    .assertNext(event -> assertThat(eventType(event)).isEqualTo("done"))
//                    .verifyComplete();
//        }
//
//        verify(chatHistoryService).saveAiMessage(eq(appId), eq(userId), anyString(), eq(1L));
//    }
//
//    @Test
//    void generate_normalPrompt_persistsAndCompletes() {
//        mockApp("html");
//        mockReviewResult("{\"safe\":true,\"route\":\"html\",\"reason\":\"ok\"}");
//        mockChatMemory();
//        mockStreamingModel("<!DOCTYPE html><html><body>Hello</body></html>");
//        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "build app");
//        when(chatHistoryService.saveUserMessage(appId, userId, "build app")).thenReturn(userHistory);
//
//        try (var ignored = UserContextTestHelper.withUserId(userId)) {
//            StepVerifier.create(aiWorkflowService.generate(appId, "build app"))
//                    .thenConsumeWhile(event -> !"done".equals(eventType(event)))
//                    .assertNext(event -> assertThat(eventType(event)).isEqualTo("done"))
//                    .verifyComplete();
//        }
//
//        verify(codePersistStrategy).persist(eq("DK_1001"), anyString());
//        verify(chatHistoryService).saveAiMessage(eq(appId), eq(userId), anyString(), eq(1L));
//        var order = inOrder(appService);
//        order.verify(appService).updateAppStatus(appId, "generating");
//        order.verify(appService).updateAppStatus(appId, "generated");
//    }
//
//    @Test
//    void generate_qualityFail_retriesUntilSuccess() {
//        mockApp("html");
//        mockReviewResult("{\"safe\":true,\"route\":\"html\",\"reason\":\"ok\"}");
//        mockChatMemory();
//        AtomicInteger counter = new AtomicInteger();
//        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
//        when(applicationContext.getBean(StreamingChatModel.class)).thenReturn(streamingModel);
//        doAnswer(invocation -> {
//            StreamingChatResponseHandler handler = invocation.getArgument(1);
//            if (counter.getAndIncrement() == 0) {
//                handler.onPartialResponse("QUALITY_FAIL");
//            } else {
//                handler.onPartialResponse("<!DOCTYPE html><html><body>ok</body></html>");
//            }
//            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
//            return null;
//        }).when(streamingModel).chat(anyList(), any(StreamingChatResponseHandler.class));
//        ChatHistory userHistory = TestDataFactory.createChatHistory(1L, appId, userId, "user", "retry me");
//        when(chatHistoryService.saveUserMessage(appId, userId, "retry me")).thenReturn(userHistory);
//
//        try (var ignored = UserContextTestHelper.withUserId(userId)) {
//            StepVerifier.create(aiWorkflowService.generate(appId, "retry me"))
//                    .thenConsumeWhile(event -> true)
//                    .verifyComplete();
//        }
//
//        assertThat(counter.get()).isEqualTo(2);
//    }
//
//    private void mockApp(String codeGenType) {
//        App app = TestDataFactory.createApp(appId, userId, "draft");
//        app.setCodeGenType(codeGenType);
//        app.setDeployKey("DK_1001");
//        when(appMapper.selectOneById(appId)).thenReturn(app);
//    }
//
//    private void mockReviewResult(String resultText) {
//        ChatResponse response = ChatResponse.builder().aiMessage(AiMessage.from(resultText)).build();
//        when(reviewChatModel.chat(any(UserMessage.class))).thenReturn(response);
//    }
//
//    private void mockChatMemory() {
//        ChatMemory chatMemory = mock(ChatMemory.class);
//        when(chatMemoryService.getChatMemory(eq(appId), eq(userId), anyString())).thenReturn(chatMemory);
//        List<ChatMessage> messages = new ArrayList<>();
//        messages.add(UserMessage.from("previous"));
//        when(chatMemory.messages()).thenReturn(messages);
//    }
//
//    private void mockStreamingModel(String... partialTokens) {
//        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
//        when(applicationContext.getBean(StreamingChatModel.class)).thenReturn(streamingModel);
//        doAnswer(invocation -> {
//            StreamingChatResponseHandler handler = invocation.getArgument(1);
//            StringBuilder full = new StringBuilder();
//            for (String token : partialTokens) {
//                full.append(token);
//                handler.onPartialResponse(token);
//            }
//            handler.onCompleteResponse(ChatResponse.builder()
//                    .aiMessage(AiMessage.from(full.toString()))
//                    .build());
//            return null;
//        }).when(streamingModel).chat(anyList(), any(StreamingChatResponseHandler.class));
//    }
//
//    private String eventType(ServerSentEvent<String> event) {
//        try {
//            JsonNode node = objectMapper.readTree(event.data());
//            return node.path("type").asText();
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
