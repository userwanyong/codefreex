package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求（个人中心，校验旧密码）
 *
 * @author wanyj
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "新密码长度须在 6-50 之间")
    private String newPassword;
}
