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
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * P7 workflow controller
 *
 * @author BanXia
 */
@Tag(name = "AI Workflow")
@RestController
@RequestMapping("/ai/workflow")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowService aiWorkflowService;

    @Operation(summary = "Trigger P7 workflow generation")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public Flux<ServerSentEvent<String>> generate(@Valid @RequestBody WorkflowGenerateRequest request) {
        return aiWorkflowService.generate(request.getAppId(), request.getMessage());
    }

    @Operation(summary = "Query workflow status")
    @GetMapping("/status")
    @AuthCheck
    public BaseResponse<WorkflowStatusResponse> getStatus(@RequestParam Long appId) {
        return ResultUtils.success(aiWorkflowService.getStatus(appId));
    }
}
