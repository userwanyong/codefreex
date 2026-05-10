package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.model.enums.ChatMessageType;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author BanXia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemoryService {

    private static final int MEMORY_TTL_MINUTES = 30;
    private static final int MEMORY_MAX_MESSAGES = 20;
    private static final String CHAT_MEMORY_KEY_PREFIX = AppConstant.REDIS_KEY_PREFIX + "chat:memory:";

    private final ChatHistoryService chatHistoryService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final Cache<String, ChatMemory> localMemoryCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(MEMORY_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    @Override
    public ChatMemory getChatMemory(Long appId, Long userId, String systemPrompt) {
        String memoryId = buildMemoryId(appId, userId);
        ChatMemory cachedMemory = localMemoryCache.getIfPresent(memoryId);
        if (cachedMemory != null) {
            return cachedMemory;
        }

        List<MemoryMessage> messages = loadMessages(memoryId, appId, userId);
        ChatMemory chatMemory = new PersistentChatMemory(
                memoryId,
                systemPrompt,
                messages,
                updatedMessages -> persistMessages(memoryId, updatedMessages)
        );
        localMemoryCache.put(memoryId, chatMemory);
        return chatMemory;
    }

    @Override
    public void clearChatMemory(Long appId, Long userId) {
        String memoryId = buildMemoryId(appId, userId);
        localMemoryCache.invalidate(memoryId);
        stringRedisTemplate.delete(memoryId);
    }

    private List<MemoryMessage> loadMessages(String memoryId, Long appId, Long userId) {
        List<MemoryMessage> messages = loadFromRedis(memoryId);
        if (messages != null) {
            return messages;
        }

        List<MemoryMessage> loadedFromDb = chatHistoryService.listRecentMessages(appId, userId, MEMORY_MAX_MESSAGES)
                .stream()
                .map(this::toMemoryMessage)
                .toList();
        persistMessages(memoryId, loadedFromDb);
        return loadedFromDb;
    }

    private List<MemoryMessage> loadFromRedis(String memoryId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(memoryId);
            if (value == null || value.isBlank()) {
                return null;
            }
            List<MemoryMessage> messages = objectMapper.readValue(value, new TypeReference<>() {
            });
            messages.forEach(message -> ChatMessageType.fromValue(message.getType()));
            return messages;
        } catch (Exception e) {
            log.warn("从 Redis 加载对话记忆失败，memoryId={}", memoryId, e);
            return null;
        }
    }

    private void persistMessages(String memoryId, List<MemoryMessage> messages) {
        try {
            String value = objectMapper.writeValueAsString(messages);
            stringRedisTemplate.opsForValue().set(memoryId, value, MEMORY_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("持久化对话记忆失败，memoryId={}", memoryId, e);
        }
    }

    private MemoryMessage toMemoryMessage(ChatHistory history) {
        ChatMessageType messageType = ChatMessageType.fromValue(history.getMessageType());
        return new MemoryMessage(messageType.getValue(), history.getMessage());
    }

    private String buildMemoryId(Long appId, Long userId) {
        return CHAT_MEMORY_KEY_PREFIX + appId + ":" + userId;
    }

    private static ChatMessage toChatMessage(MemoryMessage message) {
        ChatMessageType messageType = ChatMessageType.fromValue(message.getType());
        if (messageType == ChatMessageType.AI) {
            return AiMessage.from(message.getText());
        }
        return UserMessage.from(message.getText());
    }

    private static MemoryMessage toMemoryMessage(ChatMessage message) {
        if (message instanceof AiMessage aiMessage) {
            return new MemoryMessage(ChatMessageType.AI.getValue(), aiMessage.text());
        }
        if (message instanceof UserMessage userMessage) {
            return new MemoryMessage(ChatMessageType.USER.getValue(), userMessage.singleText());
        }
        return null;
    }

    @FunctionalInterface
    private interface MemoryPersistence {
        void persist(List<MemoryMessage> messages);
    }

    private static class PersistentChatMemory implements ChatMemory {

        private final String id;
        private final String systemPrompt;
        private final MemoryPersistence persistence;
        private final List<MemoryMessage> persistedMessages;

        private PersistentChatMemory(String id, String systemPrompt, List<MemoryMessage> messages, MemoryPersistence persistence) {
            this.id = id;
            this.systemPrompt = systemPrompt;
            this.persistence = persistence;
            this.persistedMessages = new ArrayList<>(messages);
        }

        @Override
        public Object id() {
            return id;
        }

        @Override
        public synchronized void add(ChatMessage message) {
            MemoryMessage memoryMessage = toMemoryMessage(message);
            if (memoryMessage == null) {
                return;
            }
            persistedMessages.add(memoryMessage);
            trimIfNecessary();
            persistence.persist(List.copyOf(persistedMessages));
        }

        @Override
        public synchronized List<ChatMessage> messages() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            persistedMessages.stream()
                    .map(ChatMemoryServiceImpl::toChatMessage)
                    .forEach(messages::add);
            return messages;
        }

        @Override
        public synchronized void clear() {
            persistedMessages.clear();
            persistence.persist(List.of());
        }

        private void trimIfNecessary() {
            while (persistedMessages.size() > MEMORY_MAX_MESSAGES) {
                persistedMessages.remove(0);
            }
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class MemoryMessage {
        private String type;
        private String text;
    }
}
