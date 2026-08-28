package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 账号绑定信息视图对象（来源 auth-service RPC）
 *
 * @author wanyj
 */
@Data
public class AccountBindingsVO {

    private String email;

    private Boolean emailVerified;

    private String phone;

    private Boolean phoneVerified;

    /** 是否可用验证码绑定邮箱（租户已启用任一邮箱登录方式） */
    private Boolean emailBindable;

    /** 是否可用验证码绑定手机（租户已启用短信登录方式） */
    private Boolean phoneBindable;

    /** 邮箱验证码发送方式（email:smtp / email:aliyun，空表示不可用） */
    private String emailBindMethod;

    /** 已绑定的第三方平台列表 */
    private List<OAuthBindingVO> oauthBindings;
}
