package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.model.dto.request.VisualEditRequest;
import cn.wanyj.codefreex.service.AiWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 可视化编辑
 *
 * @author BanXia
 */
@Slf4j
@Tag(name = "可视化编辑")
@RestController
@RequestMapping("/app/edit")
@RequiredArgsConstructor
public class AppEditController {

    private final AiWorkflowService aiWorkflowService;

    @Operation(summary = "可视化编辑代码")
    @PostMapping(value = "/visual", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public SseEmitter visualEdit(@Valid @RequestBody VisualEditRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);

        aiWorkflowService.visualEdit(request)
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
                    log.error("Visual edit stream error", error);
                    emitter.completeWithError(error);
                },
                emitter::complete
            );

        return emitter;
    }
}
