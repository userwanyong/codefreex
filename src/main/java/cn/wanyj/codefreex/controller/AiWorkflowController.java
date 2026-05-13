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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

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
        SseEmitter emitter = new SseEmitter(900_000L);
        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            emitterCompleted.set(true);
            emitter.complete();
            log.warn("SSE emitter timed out, appId={}", request.getAppId());
        });
        emitter.onCompletion(() -> {
            emitterCompleted.set(true);
        });
        emitter.onError(e -> {
            emitterCompleted.set(true);
            log.warn("SSE emitter error, appId={}: {}", request.getAppId(), e.getMessage());
        });

        aiWorkflowService.generate(request.getAppId(), request.getMessage())
            .subscribe(
                event -> {
                    if (emitterCompleted.get()) return;
                    try {
                        emitter.send(SseEmitter.event().data(event.data()));
                    } catch (IllegalStateException | IOException e) {
                        emitterCompleted.set(true);
                        log.warn("SSE send failed (client disconnected), appId={}: {}", request.getAppId(), e.getMessage());
                        try { emitter.complete(); } catch (Exception ignored) {}
                    }
                },
                error -> {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        log.error("Workflow stream error, appId={}", request.getAppId(), error);
                        try { emitter.completeWithError(error); } catch (Exception ignored) {}
                    }
                },
                () -> {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                }
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
