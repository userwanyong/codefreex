package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.testutil.TestDataFactory;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceImplTest {

    @Mock
    private ChatHistoryMapper chatHistoryMapper;

    @Mock
    private AppMapper appMapper;

    @InjectMocks
    private ChatHistoryServiceImpl chatHistoryService;

    @Captor
    private ArgumentCaptor<ChatHistory> chatHistoryCaptor;

    private final Long appId = 1001L;
    private final Long userId = 2001L;

    private void mockAppOwner() {
        App app = TestDataFactory.createApp(appId, userId, "draft");
        when(appMapper.selectOneById(appId)).thenReturn(app);
    }

    // ========== saveUserMessage ==========

    @Test
    void saveUserMessage_validInput_insertsWithMessageTypeUser() {
        mockAppOwner();
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatHistory result = chatHistoryService.saveUserMessage(appId, userId, "Hello AI");

        verify(chatHistoryMapper).insert(chatHistoryCaptor.capture());
        ChatHistory captured = chatHistoryCaptor.getValue();
        assertThat(captured.getMessageType()).isEqualTo("user");
        assertThat(captured.getMessage()).isEqualTo("Hello AI");
        assertThat(captured.getAppId()).isEqualTo(appId);
        assertThat(captured.getUserId()).isEqualTo(userId);
        assertThat(captured.getParentId()).isNull();
    }

    @Test
    void saveAiMessage_validInput_insertsWithMessageTypeAi() {
        mockAppOwner();
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);

        ChatHistory result = chatHistoryService.saveAiMessage(appId, userId, "AI response");

        verify(chatHistoryMapper).insert(chatHistoryCaptor.capture());
        assertThat(chatHistoryCaptor.getValue().getMessageType()).isEqualTo("ai");
    }

    @Test
    void saveAiMessage_withParentId_setsParentId() {
        mockAppOwner();
        when(chatHistoryMapper.insert(any(ChatHistory.class))).thenReturn(1);
        Long parentId = 999L;

        chatHistoryService.saveAiMessage(appId, userId, "AI response", parentId);

        verify(chatHistoryMapper).insert(chatHistoryCaptor.capture());
        assertThat(chatHistoryCaptor.getValue().getParentId()).isEqualTo(parentId);
    }

    @Test
    void saveUserMessage_blankMessage_throwsBusinessException() {
        mockAppOwner();

        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(appId, userId, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResponseCode.PARAMS_ERROR.getCode());
    }

    @Test
    void saveUserMessage_nullMessage_throwsBusinessException() {
        mockAppOwner();

        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(appId, userId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResponseCode.PARAMS_ERROR.getCode());
    }

    @Test
    void saveUserMessage_appNotFound_throwsBusinessException() {
        when(appMapper.selectOneById(appId)).thenReturn(null);

        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(appId, userId, "Hello"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResponseCode.NOT_FOUND_ERROR.getCode());
    }

    @Test
    void saveUserMessage_wrongOwner_throwsBusinessException() {
        App app = TestDataFactory.createApp(appId, 9999L, "draft");
        when(appMapper.selectOneById(appId)).thenReturn(app);

        assertThatThrownBy(() -> chatHistoryService.saveUserMessage(appId, userId, "Hello"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResponseCode.NO_AUTH_ERROR.getCode());
    }

    // ========== listChatHistory ==========

    @Test
    void listChatHistory_firstPage_returnsChronologicalOrderNoCursor() {
        mockAppOwner();
        // Simulate DB returning DESC order (newest first)
        LocalDateTime base = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        List<ChatHistory> dbResults = new ArrayList<>();
        for (int i = 4; i >= 0; i--) {
            dbResults.add(TestDataFactory.createChatHistory(
                    (long) (100 + i), appId, userId, "user", "msg" + i,
                    base.plusMinutes(i)));
        }
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(dbResults);

        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 10);

        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
        assertThat(response.getRecords()).hasSize(5);
        // Should be reversed to chronological (oldest first)
        assertThat(response.getRecords().get(0).getMessage()).isEqualTo("msg0");
        assertThat(response.getRecords().get(4).getMessage()).isEqualTo("msg4");
    }

    @Test
    void listChatHistory_hasNextTrue_returnsNextCursor() {
        mockAppOwner();
        LocalDateTime base = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        List<ChatHistory> dbResults = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            dbResults.add(TestDataFactory.createChatHistory(
                    (long) (100 + i), appId, userId, "user", "msg" + i,
                    base.plusMinutes(i)));
        }
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(dbResults);

        ChatHistoryCursorResponse response = chatHistoryService.listChatHistory(appId, userId, null, 5);

        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getNextCursor()).isNotNull();
        assertThat(response.getRecords()).hasSize(5);
    }

    @Test
    void listChatHistory_withCursor_parsesAndAppliesFilter() {
        mockAppOwner();
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        String cursor = "2024-01-15T10:30:00_12345";
        chatHistoryService.listChatHistory(appId, userId, cursor, 10);

        verify(chatHistoryMapper).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void listChatHistory_invalidCursor_throwsBusinessException() {
        mockAppOwner();

        assertThatThrownBy(() -> chatHistoryService.listChatHistory(appId, userId, "badformat", 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ResponseCode.PARAMS_ERROR.getCode());
    }

    @Test
    void listChatHistory_pageSizeClamped_to100() {
        mockAppOwner();
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        chatHistoryService.listChatHistory(appId, userId, null, 500);

        verify(chatHistoryMapper).selectListByQuery(any(QueryWrapper.class));
    }

    @Test
    void listChatHistory_pageSizeClamped_to1() {
        mockAppOwner();
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        chatHistoryService.listChatHistory(appId, userId, null, 0);

        verify(chatHistoryMapper).selectListByQuery(any(QueryWrapper.class));
    }

    // ========== listRecentMessages ==========

    @Test
    void listRecentMessages_returnsReversedToChronological() {
        mockAppOwner();
        LocalDateTime base = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
        List<ChatHistory> dbResults = new ArrayList<>();
        for (int i = 4; i >= 0; i--) {
            dbResults.add(TestDataFactory.createChatHistory(
                    (long) (100 + i), appId, userId, "user", "msg" + i,
                    base.plusMinutes(i)));
        }
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(dbResults);

        List<ChatHistory> result = chatHistoryService.listRecentMessages(appId, userId, 5);

        assertThat(result).hasSize(5);
        assertThat(result.get(0).getMessage()).isEqualTo("msg0");
        assertThat(result.get(4).getMessage()).isEqualTo("msg4");
    }

    @Test
    void listRecentMessages_limitClamped() {
        mockAppOwner();
        when(chatHistoryMapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of());

        chatHistoryService.listRecentMessages(appId, userId, 200);

        verify(chatHistoryMapper).selectListByQuery(any(QueryWrapper.class));
    }
}
