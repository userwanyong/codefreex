package cn.wanyj.codefreex.auth;

/**
 * 认证集成常量
 *
 * @author wanyj
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /**
     * 第三方登录 / 验证码自动注册账号的初始密码。
     * auth-service 侧生成的是随机密码（无法密码登录），
     * codefreex 在此类账号首次落地时统一重置为该默认密码并在界面提示，
     * 用户可在个人中心修改。
     */
    public static final String AUTO_REGISTERED_DEFAULT_PASSWORD = "123456";
}
