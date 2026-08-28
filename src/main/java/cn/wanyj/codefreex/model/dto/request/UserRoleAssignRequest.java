package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 为用户分配角色请求（全量替换）
 *
 * @author wanyj
 */
@Data
public class UserRoleAssignRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 角色 ID 列表，空列表表示清空角色 */
    private List<Long> roleIds;
}
