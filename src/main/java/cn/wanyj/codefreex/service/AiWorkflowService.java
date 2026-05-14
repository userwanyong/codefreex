package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

/**
 * P7 AI 工作流服务
 *
 * @author BanXia
 */
public interface AiWorkflowService {

    Flux<ServerSentEvent<String>> generate(Long appId, String message);

    WorkflowStatusResponse getStatus(Long appId);

    /** 注册重连订阅者，实时接收工作流事件 */
    void registerReconnectSubscriber(Long appId, Consumer<String> subscriber);

    /** 移除重连订阅者 */
    void unregisterReconnectSubscriber(Long appId, Consumer<String> subscriber);

    /** 获取已缓存的事件列表（用于断线重连回放） */
    List<String> getCachedEvents(Long appId);
}
