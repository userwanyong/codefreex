package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱注册请求（密码 + 邀请码）
 *
 * @author wanyj
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度须为 6~50 位")
    private String password;

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
