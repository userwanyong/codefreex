package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.PromptReviewResult;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * AI 生成服务接口
 *
 * @author wanyj
 */
public interface AiGenService {

    /**
     * 流式生成代码，返回 SSE 事件流
     *
     * @param appId       应用ID
     * @param userMessage 用户消息
     * @return SSE 事件流
     */
    Flux<ServerSentEvent<String>> chatToGenCode(Long appId, String userMessage);

    /**
     * 预审核提示词
     *
     * @param prompt 用户输入的提示词
     * @return 审核结果（安全判断 + 路由建议）
     */
    PromptReviewResult reviewPrompt(String prompt);

    /**
     * AI 优化提示词
     *
     * @param prompt 用户输入的原始提示词
     * @return 优化后的提示词
     */
    String optimizePrompt(String prompt);
}
