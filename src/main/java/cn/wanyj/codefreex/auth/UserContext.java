package cn.wanyj.codefreex.auth;

import cn.wanyj.codefreex.model.dto.LoginUserContext;

/**
 * 用户上下文工具类，保存当前请求的登录用户信息。
 * <p>
 * 由 {@link TokenAuthFilter} 在请求进入时通过 auth-service 的 parseToken 解析
 * Authorization: Bearer 令牌后填充，请求结束时清理。控制器与切面通过本类读取。
 *
 * @author wanyj
 */
public class UserContext {

    private static final ThreadLocal<LoginUserContext> CURRENT_USER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 获取当前登录用户（未登录返回 null）
     */
    public static LoginUserContext getLoginUser() {
        return CURRENT_USER.get();
    }

    /**
     * 获取当前登录用户ID（未登录返回 null）
     */
    public static Long getLoginUserId() {
        LoginUserContext user = getLoginUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 设置当前登录用户（仅作用于本次请求）
     */
    public static void setLoginUser(LoginUserContext user) {
        CURRENT_USER.set(user);
    }

    /**
     * 移除当前登录用户（登出 / 请求结束）
     */
    public static void removeLoginUser() {
        CURRENT_USER.remove();
    }

    /**
     * 判断当前用户是否已登录
     */
    public static boolean isLoggedIn() {
        return getLoginUser() != null;
    }

    /**
     * 判断当前用户是否是管理员
     */
    public static boolean isAdmin() {
        LoginUserContext user = getLoginUser();
        if (user == null) {
            return false;
        }
        return user.getRoles() != null &&
                (user.getRoles().contains("ROLE_ADMIN") ||
                 user.getRoles().contains("ROLE_PLATFORM_ADMIN"));
    }
}
