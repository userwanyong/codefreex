package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色保存请求（id 为空表示创建，非空表示更新）
 *
 * @author wanyj
 */
@Data
public class RoleSaveRequest {

    private Long id;

    /** 创建时必填且不可重复；创建后不可修改 */
    @Size(max = 100, message = "角色编码长度不能超过100")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    private String name;

    @Size(max = 255, message = "角色描述长度不能超过255")
    private String description;
}
