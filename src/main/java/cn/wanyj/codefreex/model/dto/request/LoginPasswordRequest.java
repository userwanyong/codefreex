package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 账号密码登录请求（用户名或邮箱 + 密码）
 *
 * @author wanyj
 */
@Data
public class LoginPasswordRequest {

    /**
     * 用户名或邮箱
     */
    @NotBlank(message = "账号不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
