package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提示词预审核请求
 *
 * @author wanyj
 */
@Data
public class PromptReviewRequest {

    @NotBlank(message = "提示词不能为空")
    @Size(max = 5000, message = "提示词最长5000字符")
    private String prompt;
}
