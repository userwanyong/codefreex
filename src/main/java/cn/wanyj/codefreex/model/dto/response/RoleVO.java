package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 角色视图对象（来源 auth-service RPC）
 *
 * @author wanyj
 */
@Data
public class RoleVO {

    private Long id;

    private String code;

    private String name;

    private String description;

    /** 1-正常，0-禁用 */
    private Integer status;

    /** 该角色拥有的权限编码列表 */
    private List<String> permissions;

    /** 是否内置角色（不可删除） */
    private Boolean builtIn;
}
