package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 权限视图对象（来源 auth-service RPC）
 *
 * @author wanyj
 */
@Data
public class PermissionVO {

    private Long id;

    private String code;

    private String name;

    private String resource;

    private String action;

    private String description;
}
