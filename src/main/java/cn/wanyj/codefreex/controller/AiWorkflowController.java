package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.WorkflowGenerateRequest;
import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import cn.wanyj.codefreex.service.AiWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 工作流
 *
 * @author BanXia
 */
@Slf4j
@Tag(name = "AI 工作流")
@RestController
@RequestMapping("/ai/workflow")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowService aiWorkflowService;

    @Operation(summary = "AI对话工作流")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public SseEmitter generate(@Valid @RequestBody WorkflowGenerateRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        aiWorkflowService.generate(request.getAppId(), request.getMessage())
            .subscribe(
                event -> {
                    try {
                        emitter.send(SseEmitter.event().data(event.data()));
                    } catch (Exception e) {
                        log.warn("SSE send failed: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("Workflow stream error", error);
                    emitter.completeWithError(error);
                },
                emitter::complete
            );

        return emitter;
    }

    @Operation(summary = "查询工作流状态")
    @GetMapping("/status")
    @AuthCheck
    public BaseResponse<WorkflowStatusResponse> getStatus(@RequestParam Long appId) {
        return ResultUtils.success(aiWorkflowService.getStatus(appId));
    }
}
