package cn.wanyj.codefreex.service;

import dev.langchain4j.memory.ChatMemory;

/**
 * @author BanXia
 */
public interface ChatMemoryService {

    /**
     * 获取应用隔离的对话记忆
     */
    ChatMemory getChatMemory(Long appId, Long userId, String systemPrompt);

    /**
     * 清理应用对话记忆
     */
    void clearChatMemory(Long appId, Long userId);
}
