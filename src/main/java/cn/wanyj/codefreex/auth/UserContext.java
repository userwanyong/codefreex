package cn.wanyj.codefreex.auth;

import cn.wanyj.codefreex.model.dto.LoginUserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类，从 Session 中获取当前登录用户信息
 */
public class UserContext {

    private static final String SESSION_USER_KEY = "loginUser";

    private UserContext() {
    }

    /**
     * 获取当前登录用户
     */
    public static LoginUserContext getLoginUser() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        return (LoginUserContext) request.getSession().getAttribute(SESSION_USER_KEY);
    }

    /**
     * 获取当前登录用户ID
     */
    public static Long getLoginUserId() {
        LoginUserContext user = getLoginUser();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 设置当前登录用户到 Session
     */
    public static void setLoginUser(LoginUserContext user) {
        HttpServletRequest request = getRequest();
        if (request != null) {
            request.getSession().setAttribute(SESSION_USER_KEY, user);
        }
    }

    /**
     * 移除当前登录用户（登出）
     */
    public static void removeLoginUser() {
        HttpServletRequest request = getRequest();
        if (request != null) {
            request.getSession().removeAttribute(SESSION_USER_KEY);
            request.getSession().invalidate();
        }
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

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
