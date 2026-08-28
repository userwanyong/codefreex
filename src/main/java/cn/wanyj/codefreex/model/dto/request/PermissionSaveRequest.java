package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限创建请求
 *
 * @author wanyj
 */
@Data
public class PermissionSaveRequest {

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String name;

    @Size(max = 100, message = "资源长度不能超过100")
    private String resource;

    @Size(max = 50, message = "操作长度不能超过50")
    private String action;

    @Size(max = 255, message = "描述长度不能超过255")
    private String description;
}
