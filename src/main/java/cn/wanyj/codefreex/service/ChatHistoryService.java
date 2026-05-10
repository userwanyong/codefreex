package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.model.entity.ChatHistory;

import java.util.List;

/**
 * @author BanXia
 */
public interface ChatHistoryService {

    /**
     * 保存用户消息
     */
    ChatHistory saveUserMessage(Long appId, Long userId, String message);

    /**
     * 保存 AI 消息
     */
    ChatHistory saveAiMessage(Long appId, Long userId, String message);

    /**
     * 保存 AI 消息，并指定父消息
     */
    ChatHistory saveAiMessage(Long appId, Long userId, String message, Long parentId);

    /**
     * 游标分页查询历史消息
     */
    ChatHistoryCursorResponse listChatHistory(Long appId, Long userId, String cursor, int size);

    /**
     * 查询最近的历史消息（按时间正序返回）
     */
    List<ChatHistory> listRecentMessages(Long appId, Long userId, int limit);

    /**
     * 删除指定消息
     */
    void deleteById(Long id);

    /**
     * 删除指定应用的所有对话历史
     */
    void deleteByAppId(Long appId);
}
