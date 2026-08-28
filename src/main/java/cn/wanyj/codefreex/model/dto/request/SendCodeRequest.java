package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送登录验证码请求（邮箱/短信）
 *
 * @author wanyj
 */
@Data
public class SendCodeRequest {

    /**
     * 登录方式编码，如 email:smtp / sms:aliyun
     */
    @NotBlank(message = "登录方式不能为空")
    private String method;

    /**
     * 接收目标（邮箱地址或手机号）
     */
    @NotBlank(message = "接收目标不能为空")
    private String target;
}
