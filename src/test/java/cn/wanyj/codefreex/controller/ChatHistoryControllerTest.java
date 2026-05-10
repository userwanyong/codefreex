package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.testutil.UserContextTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author BanXia
 */
@ExtendWith(MockitoExtension.class)
class ChatHistoryControllerTest {

    @Mock
    private ChatHistoryService chatHistoryService;

    @InjectMocks
    private ChatHistoryController chatHistoryController;

    @Test
    void listChatHistory_usesLoginUserIdAndReturnsResponse() {
        Long appId = 1001L;
        Long userId = 2001L;
        ChatHistoryCursorResponse expected = ChatHistoryCursorResponse.of(java.util.List.of(), null, false);
        when(chatHistoryService.listChatHistory(appId, userId, null, 20)).thenReturn(expected);

        try (var ignored = UserContextTestHelper.withUserId(userId)) {
            var response = chatHistoryController.listChatHistory(appId, null, 20);

            assertThat(response.getCode()).isEqualTo(0);
            assertThat(response.getData()).isSameAs(expected);
        }

        verify(chatHistoryService).listChatHistory(appId, userId, null, 20);
    }
}
