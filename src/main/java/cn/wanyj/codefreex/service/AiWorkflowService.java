package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * P7 AI 工作流服务
 *
 * @author BanXia
 */
public interface AiWorkflowService {

    Flux<ServerSentEvent<String>> generate(Long appId, String message);

    WorkflowStatusResponse getStatus(Long appId);
}
