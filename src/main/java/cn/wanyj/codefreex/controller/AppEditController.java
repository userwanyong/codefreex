package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.model.dto.request.VisualEditRequest;
import cn.wanyj.codefreex.service.AiWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Visual edit controller
 *
 * @author BanXia
 */
@Tag(name = "Visual Edit")
@RestController
@RequestMapping("/app/edit")
@RequiredArgsConstructor
public class AppEditController {

    private final AiWorkflowService aiWorkflowService;

    @Operation(summary = "Update code by visual edit instruction")
    @PostMapping(value = "/visual", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public Flux<ServerSentEvent<String>> visualEdit(@Valid @RequestBody VisualEditRequest request) {
        return aiWorkflowService.visualEdit(request);
    }
}
