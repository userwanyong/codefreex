package cn.wanyj.codefreex.auth;

import cn.wanyj.auth.api.protobuf.TokenValidationResult;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import jakarta.annotation.Resource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

/**
 * 令牌鉴权过滤器：解析请求头 Authorization: Bearer &lt;accessToken&gt;，
 * 调用 auth-service 的 parseToken 完成校验（签名 + 有效期 + 黑名单），
 * 将用户身份写入 {@link UserContext} 供后续切面与控制器使用。
 * <p>
 * 未携带令牌或令牌无效时按匿名放行，由 @AuthCheck 切面对受保护接口拒绝。
 *
 * @author wanyj
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TokenAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Resource
    private AuthRpcClient authRpcClient;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String accessToken = extractBearerToken(request);
            if (accessToken != null && !accessToken.isBlank()) {
                LoginUserContext user = resolveUser(accessToken);
                if (user != null) {
                    UserContext.setLoginUser(user);
                }
            }
            chain.doFilter(request, response);
        } finally {
            UserContext.removeLoginUser();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /**
     * 解析令牌；认证服务不可用或令牌无效时不抛错（匿名处理），
     * 避免服务波动导致公开接口不可用。
     */
    private LoginUserContext resolveUser(String accessToken) {
        try {
            TokenValidationResult result = authRpcClient.parseToken(accessToken);
            if (result == null || !result.getValid()) {
                return null;
            }
            LoginUserContext ctx = new LoginUserContext();
            ctx.setUserId(result.getUserId());
            ctx.setUsername(result.getUsername());
            ctx.setRoles(new ArrayList<>(result.getRolesList()));
            ctx.setPermissions(new ArrayList<>(result.getPermissionsList()));
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }
}
