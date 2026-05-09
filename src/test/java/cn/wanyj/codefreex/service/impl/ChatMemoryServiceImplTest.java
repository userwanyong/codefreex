package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.testutil.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMemoryServiceImplTest {

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ChatMemoryServiceImpl chatMemoryService;

    private final Long appId = 1001L;
    private final Long userId = 2001L;
    private final String systemPrompt = "You are a helpful assistant.";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ========== getChatMemory — 三级缓存 ==========

    @Test
    void getChatMemory_firstCall_loadsFromDb() {
        // Redis returns null
        when(valueOperations.get(anyString())).thenReturn(null);
        // DB returns some history
        List<ChatHistory> dbHistory = List.of(
                TestDataFactory.createChatHistory(1L, appId, userId, "user", "Hello", LocalDateTime.now().minusMinutes(2)),
                TestDataFactory.createChatHistory(2L, appId, userId, "ai", "Hi there", LocalDateTime.now().minusMinutes(1))
        );
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(dbHistory);

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        assertThat(memory).isNotNull();
        verify(chatHistoryService).listRecentMessages(appId, userId, 20);
        // Should persist to Redis
        verify(valueOperations).set(anyString(), anyString(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getChatMemory_redisHit_skipsDb() {
        // Redis returns valid JSON
        String redisJson = "[{\"type\":\"user\",\"text\":\"cached msg\"}]";
        when(valueOperations.get(anyString())).thenReturn(redisJson);

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        assertThat(memory).isNotNull();
        // Should NOT query DB
        verify(chatHistoryService, never()).listRecentMessages(anyLong(), anyLong(), anyInt());
    }

    @Test
    void getChatMemory_secondCall_returnsCachedFromCaffeine() {
        // First call: Redis null, DB returns data
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        // DB should only be called once (Caffeine cache hit on second call)
        verify(chatHistoryService, times(1)).listRecentMessages(appId, userId, 20);
    }

    // ========== clearChatMemory ==========

    @Test
    void clearChatMemory_invalidatesCacheAndRedis() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        // First call to populate cache
        chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        // Clear
        chatMemoryService.clearChatMemory(appId, userId);
        // Second call should reload from DB
        chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        verify(stringRedisTemplate).delete(anyString());
        verify(chatHistoryService, times(2)).listRecentMessages(appId, userId, 20);
    }

    // ========== PersistentChatMemory — add / messages / clear ==========

    @Test
    void persistentChatMemory_messages_prependsSystemMessage() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        List<ChatMessage> messages = memory.messages();

        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) messages.get(0)).text()).isEqualTo(systemPrompt);
    }

    @Test
    void persistentChatMemory_add_persistsToRedis() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        // Reset mock to clear setup interactions
        clearInvocations(valueOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        memory.add(UserMessage.from("Hello"));

        // Should persist to Redis after add
        verify(valueOperations).set(anyString(), anyString(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    void persistentChatMemory_trimAt20_removesOldest() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);

        // Add 25 messages
        for (int i = 0; i < 25; i++) {
            memory.add(UserMessage.from("msg" + i));
        }

        List<ChatMessage> messages = memory.messages();
        // 1 SystemMessage + 20 user messages = 21
        assertThat(messages).hasSize(21);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        // First user message should be "msg5" (oldest 5 trimmed)
        assertThat(((UserMessage) messages.get(1)).singleText()).isEqualTo("msg5");
        assertThat(((UserMessage) messages.get(20)).singleText()).isEqualTo("msg24");
    }

    @Test
    void persistentChatMemory_clear_emptiesAndPersists() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(appId, userId, 20)).thenReturn(List.of());

        ChatMemory memory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        memory.add(UserMessage.from("Hello"));
        memory.clear();

        List<ChatMessage> messages = memory.messages();
        // Only SystemMessage remains
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
    }

    // ========== 应用隔离 ==========

    @Test
    void twoApps_differentMemoryIds_isolated() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatHistoryService.listRecentMessages(eq(1001L), eq(userId), eq(20))).thenReturn(List.of());
        when(chatHistoryService.listRecentMessages(eq(1002L), eq(userId), eq(20))).thenReturn(List.of());

        ChatMemory memory1 = chatMemoryService.getChatMemory(1001L, userId, systemPrompt);
        ChatMemory memory2 = chatMemoryService.getChatMemory(1002L, userId, systemPrompt);

        memory1.add(UserMessage.from("App1 message"));
        memory2.add(UserMessage.from("App2 message"));

        List<ChatMessage> messages1 = memory1.messages();
        List<ChatMessage> messages2 = memory2.messages();

        // Each should have SystemMessage + 1 user message
        assertThat(messages1).hasSize(2);
        assertThat(messages2).hasSize(2);
        assertThat(((UserMessage) messages1.get(1)).singleText()).isEqualTo("App1 message");
        assertThat(((UserMessage) messages2.get(1)).singleText()).isEqualTo("App2 message");
    }
}
