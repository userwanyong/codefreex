package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * P7 工作流生成请求
 *
 * @author BanXia
 */
@Data
public class WorkflowGenerateRequest {

    @NotNull(message = "应用 ID 不能为空")
    private Long appId;

    @NotBlank(message = "生成提示词不能为空")
    private String message;
}
