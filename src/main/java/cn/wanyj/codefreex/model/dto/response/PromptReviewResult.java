package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 提示词预审核结果
 *
 * @author wanyj
 */
@Data
public class PromptReviewResult {

    /**
     * 是否安全
     */
    private boolean safe;

    /**
     * 安全审查说明（不安全时给出原因）
     */
    private String reason;

    /**
     * 路由建议（html / multi_file / vue_project）
     */
    private String routeSuggestion;

    /**
     * 优化后的提示词（可选）
     */
    private String optimizedPrompt;

    public static PromptReviewResult safe(String routeSuggestion) {
        PromptReviewResult result = new PromptReviewResult();
        result.setSafe(true);
        result.setRouteSuggestion(routeSuggestion);
        return result;
    }

    public static PromptReviewResult unsafe(String reason) {
        PromptReviewResult result = new PromptReviewResult();
        result.setSafe(false);
        result.setReason(reason);
        return result;
    }
}
