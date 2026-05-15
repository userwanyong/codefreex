package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.service.PrometheusMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Prometheus 自定义指标服务实现
 *
 * @author wanyj
 */
@Slf4j
@Service
public class PrometheusMetricsServiceImpl implements PrometheusMetricsService {

    private final MeterRegistry meterRegistry;

    public PrometheusMetricsServiceImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void recordModelCall(Long userId, Long appId, String modelId,
                                int inputTokens, int outputTokens, int totalTokens,
                                long latencyMs, String status, String errorInfo, String source) {
        try {
            String userIdStr = userId != null ? String.valueOf(userId) : "unknown";
            String appIdStr = appId != null ? String.valueOf(appId) : "unknown";
            String model = modelId != null ? modelId : "unknown";
            String src = source != null ? source : "unknown";

            // 1. 调用次数 Counter（Micrometer 自动添加 _total 后缀）
            Counter.builder("ai_model_call")
                    .description("AI 模型调用总次数")
                    .tag("model", model)
                    .tag("user_id", userIdStr)
                    .tag("app_id", appIdStr)
                    .tag("status", status)
                    .tag("source", src)
                    .register(meterRegistry)
                    .increment();

            // 2. 输入 Token（导出为 ai_model_input_tokens_total）
            Counter.builder("ai_model_input_tokens")
                    .description("AI 模型输入 Token 累计消耗")
                    .tag("model", model)
                    .tag("user_id", userIdStr)
                    .tag("app_id", appIdStr)
                    .tag("source", src)
                    .register(meterRegistry)
                    .increment(inputTokens);

            // 3. 输出 Token（导出为 ai_model_output_tokens_total）
            Counter.builder("ai_model_output_tokens")
                    .description("AI 模型输出 Token 累计消耗")
                    .tag("model", model)
                    .tag("user_id", userIdStr)
                    .tag("app_id", appIdStr)
                    .tag("source", src)
                    .register(meterRegistry)
                    .increment(outputTokens);

            // 4. 总 Token（导出为 ai_model_total_tokens_total）
            Counter.builder("ai_model_total_tokens")
                    .description("AI 模型总 Token 累计消耗")
                    .tag("model", model)
                    .tag("user_id", userIdStr)
                    .tag("app_id", appIdStr)
                    .tag("source", src)
                    .register(meterRegistry)
                    .increment(totalTokens);

            // 5. 响应时长 Timer（Micrometer 自动添加 _seconds 后缀，导出为 ai_model_call_duration_seconds）
            if ("success".equals(status)) {
                Timer.builder("ai_model_call_duration")
                        .description("AI 模型调用响应时长")
                        .tag("model", model)
                        .tag("user_id", userIdStr)
                        .tag("app_id", appIdStr)
                        .tag("source", src)
                        .register(meterRegistry)
                        .record(latencyMs, TimeUnit.MILLISECONDS);
            }

            // 6. 错误分类 Counter（导出为 ai_model_errors_total）
            if ("fail".equals(status) && errorInfo != null) {
                String errorType = classifyError(errorInfo);
                Counter.builder("ai_model_errors")
                        .description("AI 模型调用错误次数")
                        .tag("model", model)
                        .tag("error_type", errorType)
                        .tag("source", src)
                        .register(meterRegistry)
                        .increment();
            }
        } catch (Exception e) {
            log.warn("记录 Prometheus 指标失败", e);
        }
    }

    /**
     * 对错误信息进行分类，避免高基数标签
     */
    private String classifyError(String errorInfo) {
        if (errorInfo == null) {
            return "unknown";
        }
        String lower = errorInfo.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "timeout";
        }
        if (lower.contains("rate limit") || lower.contains("429") || lower.contains("too many requests")) {
            return "rate_limit";
        }
        if (lower.contains("empty") || lower.contains("为空")) {
            return "empty_response";
        }
        if (lower.contains("content_filter") || lower.contains("content policy")) {
            return "content_filter";
        }
        if (lower.contains("auth") || lower.contains("401") || lower.contains("403")
                || lower.contains("api key") || lower.contains("unauthorized")) {
            return "auth_error";
        }
        if (lower.contains("network") || lower.contains("connection") || lower.contains("refused")) {
            return "network_error";
        }
        if (lower.contains("500") || lower.contains("internal")) {
            return "server_error";
        }
        return "other";
    }
}
