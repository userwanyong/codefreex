package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@ActiveProfiles("test")
@Transactional
@Import(IntegrationTestConfig.class)
class ChatHistoryServiceIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    private final Long appId = 1001L;
    private final Long userId = 2001L;

    // ========== 验证项 1: 单轮对话消息保存 ==========

    @Test
    void saveUserMessage_persistsToH2_queryableById() {
        ChatHistory saved = chatHistoryService.saveUserMessage(appId, userId, "Hello AI");

        assertThat(saved.getId()).isNotNull();
        ChatHistory loaded = chatHistoryMapper.selectOneById(saved.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getMessage()).isEqualTo("Hello AI");
        assertThat(loaded.getMessageType()).isEqualTo("user");
        assertThat(loaded.getAppId()).isEqualTo(appId);
        assertThat(loaded.getUserId()).isEqualTo(userId);
        assertThat(loaded.getCreateTime()).isNotNull();
    }

    @Test
    void saveUserAndAiMessage_bothSavedWithCorrectTypes() {
        ChatHistory userMsg = chatHistoryService.saveUserMessage(appId, userId, "User says hi");
        ChatHistory aiMsg = chatHistoryService.saveAiMessage(appId, userId, "AI says hello", userMsg.getId());

        assertThat(userMsg.getMessageType()).isEqualTo("user");
        assertThat(aiMsg.getMessageType()).isEqualTo("ai");
        assertThat(aiMsg.getParentId()).isEqualTo(userMsg.getId());
    }

    // ========== 验证项 2: 多轮对话 parent 关联 ==========

    @Test
    void saveAiMessage_withParentId_relationshipQueryable() {
        ChatHistory userMsg = chatHistoryService.saveUserMessage(appId, userId, "Question");
        ChatHistory aiMsg = chatHistoryService.saveAiMessage(appId, userId, "Answer", userMsg.getId());

        ChatHistory loaded = chatHistoryMapper.selectOneById(aiMsg.getId());
        assertThat(loaded.getParentId()).isEqualTo(userMsg.getId());
    }

    // ========== 验证项 6: 游标分页 ==========

    @Test
    void listChatHistory_cursorPagination_returnsAllRecords() {
        // Insert 5 messages
        for (int i = 0; i < 5; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        // Fetch all in one page
        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 10);

        assertThat(response.getRecords()).hasSize(5);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void listChatHistory_cursorPagination_pageSizeRespected() {
        // Insert 5 messages
        for (int i = 0; i < 5; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        // Request page size 3 — should return 3 with hasNext=true
        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 3);

        assertThat(response.getRecords()).hasSize(3);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void listChatHistory_cursorPagination_chronologicalOrder() {
        // Insert messages
        for (int i = 0; i < 5; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 10);
        List<ChatHistory> records = response.getRecords();

        // Verify chronological order (oldest first)
        for (int i = 1; i < records.size(); i++) {
            assertThat(records.get(i).getCreateTime())
                    .isAfterOrEqualTo(records.get(i - 1).getCreateTime());
        }
    }

    @Test
    void listChatHistory_cursorPagination_hasNextFalseOnLastPage() {
        for (int i = 0; i < 3; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 5);

        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getRecords()).hasSize(3);
    }

    // ========== listRecentMessages ==========

    @Test
    void listRecentMessages_returnsCorrectCount() {
        for (int i = 0; i < 10; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        List<ChatHistory> result = chatHistoryService.listRecentMessages(appId, userId, 5);

        assertThat(result).hasSize(5);
    }

    @Test
    void listRecentMessages_chronologicalOrder() {
        for (int i = 0; i < 5; i++) {
            chatHistoryService.saveUserMessage(appId, userId, "msg" + i);
        }

        List<ChatHistory> result = chatHistoryService.listRecentMessages(appId, userId, 5);

        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).getCreateTime())
                    .isAfterOrEqualTo(result.get(i - 1).getCreateTime());
        }
    }

    // ========== 权限校验 ==========

    @Test
    void saveMessage_wrongOwner_throwsException() {
        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(appId, 9999L, "Hello"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveMessage_appNotFound_throwsException() {
        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(9999L, userId, "Hello"))
                .isInstanceOf(BusinessException.class);
    }
}
