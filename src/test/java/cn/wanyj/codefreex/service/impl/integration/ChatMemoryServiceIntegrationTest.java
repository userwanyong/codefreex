package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestConfig.class)
class ChatMemoryServiceIntegrationTest {

    @Autowired
    private ChatMemoryService chatMemoryService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    private final Long appId = 1001L;
    private final Long appId2 = 1002L;
    private final Long userId = 2001L;
    private final String systemPrompt = "You are a code generator.";

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        // Redis returns null by default (cache miss)
        lenient().when(valueOps.get(anyString())).thenReturn(null);
    }

    // ========== 验证项 3: 记忆加载与 TTL ==========

    @Test
    void getChatMemory_loadsFromDbWhenEmpty() {
        // Seed DB with chat history
        chatHistoryService.saveUserMessage(appId, userId, "What is HTML?");
        chatHistoryService.saveAiMessage(appId, userId, "HTML is a markup language.");

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        assertThat(memory).isNotNull();
        List<ChatMessage> messages = memory.messages();
        // SystemMessage + 2 history messages
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo(systemPrompt);
    }

    @Test
    void getChatMemory_cachesInRedis() {
        chatHistoryService.saveUserMessage(appId, userId, "Hello");

        ChatMemory first = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        ChatMemory second = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        // Same instance from Caffeine local cache
        assertThat(first).isSameAs(second);
    }

    @Test
    void clearChatMemory_removesFromRedisAndLocal() {
        chatHistoryService.saveUserMessage(appId, userId, "Hello");

        // Load into cache
        chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        // Clear
        chatMemoryService.clearChatMemory(appId, userId);

        verify(stringRedisTemplate).delete(anyString());
    }

    @Test
    void ttlExpiry_reloadFromDb() {
        // Clear Caffeine cache from previous tests
        chatMemoryService.clearChatMemory(appId, userId);

        chatHistoryService.saveUserMessage(appId, userId, "msg1");

        // First call — loads from DB
        ChatMemory first = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        assertThat(first.messages()).hasSize(2); // SystemMessage + 1

        // Add more data to DB
        chatHistoryService.saveAiMessage(appId, userId, "reply1");
        chatHistoryService.saveUserMessage(appId, userId, "msg2");

        // Clear cache to simulate TTL expiry
        chatMemoryService.clearChatMemory(appId, userId);

        // Second call — should reload from DB with new messages
        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        List<ChatMessage> messages = memory.messages();
        // SystemMessage + 3 messages
        assertThat(messages).hasSize(4);
    }

    // ========== 验证项 4: 应用隔离 ==========

    @Test
    void parallelApps_memoryIsolated() {
        // Seed different histories for two apps
        chatHistoryService.saveUserMessage(appId, userId, "App1 question");
        chatHistoryService.saveAiMessage(appId, userId, "App1 answer");

        // For app2, need a different user (2002) who owns app 1002
        chatHistoryService.saveUserMessage(appId2, 2002L, "App2 question");

        ChatMemory memory1 = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        ChatMemory memory2 = chatMemoryService.getChatMemory(appId2, 2002L, systemPrompt);

        // Add different messages
        memory1.add(UserMessage.from("App1 new msg"));
        memory2.add(UserMessage.from("App2 new msg"));

        List<ChatMessage> messages1 = memory1.messages();
        List<ChatMessage> messages2 = memory2.messages();

        // Find the user messages (skip SystemMessage)
        String app1UserMsg = messages1.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .filter(t -> t.equals("App1 new msg"))
                .findFirst().orElse(null);
        String app2UserMsg = messages2.stream()
                .filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText())
                .filter(t -> t.equals("App2 new msg"))
                .findFirst().orElse(null);

        assertThat(app1UserMsg).isEqualTo("App1 new msg");
        assertThat(app2UserMsg).isEqualTo("App2 new msg");

        // Verify isolation: memory1 should NOT contain App2's message
        boolean app1HasApp2Msg = messages1.stream()
                .anyMatch(m -> m instanceof UserMessage && ((UserMessage) m).singleText().equals("App2 new msg"));
        assertThat(app1HasApp2Msg).isFalse();
    }
}
