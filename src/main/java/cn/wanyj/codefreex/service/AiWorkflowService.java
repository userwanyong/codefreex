package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.request.VisualEditRequest;
import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * P7 AI 宸ヤ綔娴佹湇鍔?
 *
 * @author BanXia
 */
public interface AiWorkflowService {

    Flux<ServerSentEvent<String>> generate(Long appId, String message);

    Flux<ServerSentEvent<String>> visualEdit(VisualEditRequest request);

    WorkflowStatusResponse getStatus(Long appId);
}
