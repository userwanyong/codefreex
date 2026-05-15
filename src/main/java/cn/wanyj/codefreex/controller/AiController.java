package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.dto.request.PromptReviewRequest;
import cn.wanyj.codefreex.model.dto.response.PromptReviewResult;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.AiGenService;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;

/**
 * AI 对话接口
 *
 * @author wanyj
 */
@Tag(name = "AI 对话接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiGenService aiGenService;
    private final UserInfoService userInfoService;
    private final CreditTransactionService creditTransactionService;
    private final ChatHistoryMapper chatHistoryMapper;

    @Operation(summary = "AI 对话生成代码（SSE 流式）")
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public Flux<ServerSentEvent<String>> chatToGenCode(
            @RequestParam Long appId,
            @RequestParam String message) {
        // 码点预扣减（仅后续对话扣减，首次生成已在创建应用时扣减）
        Long userId = UserContext.getLoginUserId();
        if (userId == null) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR, "未登录");
        }
        long historyCount = chatHistoryMapper.selectCountByQuery(
                QueryWrapper.create().where(CHAT_HISTORY.APP_ID.eq(appId))
        );
        boolean isFirstGenerate = historyCount == 0;

        if (!isFirstGenerate) {
            int cost = AppConstant.CHAT_ROUND_COST;
            UserInfo userInfo = userInfoService.getUserInfo(userId);
            if (userInfo == null || userInfo.getRemainingCredits() < cost) {
                throw new BusinessException(ResponseCode.OPERATION_ERROR,
                        "码点不足，需要 " + cost + " 码点，请先兑换码点");
            }
            userInfoService.deductCredits(userId, cost);

            UserInfo updatedUserInfo = userInfoService.getUserInfo(userId);
            creditTransactionService.recordTransaction(
                    userId,
                    CreditTransactionType.CONSUME,
                    -cost,
                    updatedUserInfo.getRemainingCredits(),
                    CreditSourceType.AI_CHAT,
                    appId,
                    "对话轮次",
                    null
            );
        }

        return aiGenService.chatToGenCode(appId, message);
    }

    @Operation(summary = "预审核提示词")
    @PostMapping("/prompt/review")
    @AuthCheck
    public BaseResponse<PromptReviewResult> reviewPrompt(@Valid @RequestBody PromptReviewRequest request) {
        return ResultUtils.success(aiGenService.reviewPrompt(request.getPrompt()));
    }

    @Operation(summary = "AI 优化提示词")
    @PostMapping("/prompt/optimize")
    @AuthCheck
    public BaseResponse<String> optimizePrompt(@Valid @RequestBody PromptReviewRequest request) {
        return ResultUtils.success(aiGenService.optimizePrompt(request.getPrompt()));
    }
}
