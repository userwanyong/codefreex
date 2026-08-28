package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 为角色分配权限请求（全量替换）
 *
 * @author wanyj
 */
@Data
public class RolePermissionAssignRequest {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    /** 权限 ID 列表，空列表表示清空权限 */
    private List<Long> permissionIds;
}
