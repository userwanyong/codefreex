package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 编辑应用请求
 *
 * @author wanyj
 */
@Data
public class AppEditRequest {

    /**
     * 应用ID（必填）
     */
    @NotNull(message = "应用ID不能为空")
    private Long id;

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
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 是否公开（0-私有，1-公开）
     */
    private Integer isPublic;

    /**
     * 标签ID列表（关联预设标签）
     */
    private List<Long> tagIds;

    /**
     * 代码生成模式：html / multi_file / vue
     */
    private String codeGenType;
}
