package cn.wanyj.codefreex.service;

/**
 * Prometheus 自定义指标服务
 * <p>
 * 负责在 AI 模型调用链路中记录业务指标，供 Prometheus 采集。
 * <p>
 * 指标设计：
 * - ai_model_call_total            Counter   模型调用总次数（含维度：model, user_id, app_id, status, source）
 * - ai_model_input_tokens_total    Counter   输入 Token 累计消耗
 * - ai_model_output_tokens_total   Counter   输出 Token 累计消耗
 * - ai_model_total_tokens_total    Counter   总 Token 累计消耗
 * - ai_model_call_duration_seconds Timer     模型调用响应时长（含直方图，支持 P50/P90/P95/P99）
 * - ai_model_errors_total          Counter   模型调用失败次数（含 error_type 维度）
 *
 * @author wanyj
 */
public interface PrometheusMetricsService {

    /**
     * 记录一次 AI 模型调用的完整指标
     *
     * @param userId       用户 ID
     * @param appId        应用 ID
     * @param modelId      模型名称
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param totalTokens  总 Token 数
     * @param latencyMs    响应时长（毫秒）
     * @param status       调用状态（success / fail）
     * @param errorInfo    失败时的错误信息（成功传 null）
     * @param source       调用来源（gen / workflow）
     */
    void recordModelCall(Long userId, Long appId, String modelId,
                         int inputTokens, int outputTokens, int totalTokens,
                         long latencyMs, String status, String errorInfo, String source);
}
