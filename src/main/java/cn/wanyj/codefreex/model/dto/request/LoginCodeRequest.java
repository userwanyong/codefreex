package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 验证码登录请求；新用户（目标不存在将自动注册）须携带有效邀请码
 *
 * @author wanyj
 */
@Data
public class LoginCodeRequest {

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

    /**
     * 验证码（6 位）
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 邀请码（新用户必填，老用户无需填写）
     */
    private String inviteCode;
}
