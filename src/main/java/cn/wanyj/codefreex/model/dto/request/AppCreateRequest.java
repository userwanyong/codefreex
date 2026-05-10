package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建应用请求
 *
 * @author wanyj
 */
@Data
public class AppCreateRequest {

    /**
     * 应用名称
     */
    @Size(max = 256, message = "应用名称最长256字符")
    private String appName;

    /**
     * 应用描述
     */
    @Size(max = 1024, message = "应用描述最长1024字符")
    private String description;

    /**
     * 应用初始化的 prompt（必填）
     */
    @NotBlank(message = "生成提示词不能为空")
    private String initPrompt;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 代码生成模式：html / multi_file / vue
     */
    private String codeGenType;
}
