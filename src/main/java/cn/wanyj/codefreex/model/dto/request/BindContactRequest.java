package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 绑定/换绑邮箱或手机号请求（须先发送验证码到该目标）
 *
 * @author wanyj
 */
@Data
public class BindContactRequest {

    @NotBlank(message = "绑定目标不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
