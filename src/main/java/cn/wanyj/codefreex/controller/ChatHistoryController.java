package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.response.ChatHistoryCursorResponse;
import cn.wanyj.codefreex.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话历史查询接口
 *
 * @author BanXia
 */
@Tag(name = "对话历史查询接口")
@RestController
@RequestMapping("/chat/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @Operation(summary = "分页查询对话历史")
    @GetMapping("/list")
    @AuthCheck
    public BaseResponse<ChatHistoryCursorResponse> listChatHistory(
            @RequestParam Long appId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(chatHistoryService.listChatHistory(appId, userId, cursor, size));
    }
}
