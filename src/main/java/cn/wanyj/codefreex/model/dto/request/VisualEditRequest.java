package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 可视化编辑请求
 *
 * @author BanXia
 */
@Data
public class VisualEditRequest {

    @NotNull(message = "应用 ID 不能为空")
    private Long appId;

    @NotBlank(message = "元素选择器不能为空")
    private String selector;

    @NotBlank(message = "原始元素 HTML 不能为空")
    private String selectedHtml;

    @NotBlank(message = "修改需求不能为空")
    private String instruction;

    private String targetFile;
}
