package cn.wanyj.codefreex.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户上下文信息，存入 Session
 * @author wanyj
 */
@Data
public class LoginUserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String email;

    private String phone;

    private String nickname;

    private String avatar;

    private Long tenantId;

    private List<String> roles;

    private List<String> permissions;
}
