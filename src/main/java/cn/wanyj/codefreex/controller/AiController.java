package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.PromptReviewRequest;
import cn.wanyj.codefreex.model.dto.response.PromptReviewResult;
import cn.wanyj.codefreex.service.AiGenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

    @Operation(summary = "AI 对话生成代码（SSE 流式）")
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public Flux<ServerSentEvent<String>> chatToGenCode(
            @RequestParam Long appId,
            @RequestParam String message) {
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
