package cn.wanyj.codefreex.testutil;

import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;

import java.time.LocalDateTime;

/**
 * 测试数据工厂，减少测试类中的样板代码
 */
public class TestDataFactory {

    private TestDataFactory() {
    }

    public static App createApp(Long id, Long userId, String status) {
        App app = new App();
        app.setId(id);
        app.setUserId(userId);
        app.setAppName("TestApp_" + id);
        app.setDescription("Test description");
        app.setInitPrompt("build something");
        app.setCodeGenType("html_single");
        app.setStatus(status);
        app.setDeployKey("DK_" + id);
        app.setIsPublic(0);
        app.setIsFeatured(0);
        app.setPriority(0);
        app.setViewCount(0);
        app.setLikeCount(0);
        app.setIsDelete(0);
        app.setEditTime(LocalDateTime.now());
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        return app;
    }

    public static ChatHistory createChatHistory(Long id, Long appId, Long userId, String messageType, String message) {
        return createChatHistory(id, appId, userId, messageType, message, null, LocalDateTime.now());
    }

    public static ChatHistory createChatHistory(Long id, Long appId, Long userId, String messageType, String message, LocalDateTime createTime) {
        return createChatHistory(id, appId, userId, messageType, message, null, createTime);
    }

    public static ChatHistory createChatHistory(Long id, Long appId, Long userId, String messageType, String message, Long parentId, LocalDateTime createTime) {
        ChatHistory history = new ChatHistory();
        history.setId(id);
        history.setAppId(appId);
        history.setUserId(userId);
        history.setMessageType(messageType);
        history.setMessage(message);
        history.setParentId(parentId);
        history.setCreateTime(createTime);
        history.setUpdateTime(createTime);
        history.setIsDelete(0);
        return history;
    }
}
