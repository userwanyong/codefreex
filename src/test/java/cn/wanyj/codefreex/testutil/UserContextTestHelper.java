package cn.wanyj.codefreex.testutil;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * 封装 UserContext 静态方法的 mock，用于单元测试。
 *
 * <pre>{@code
 * try (var ignored = UserContextTestHelper.withUserId(2001L)) {
 *     // 测试代码中调用 UserContext.getLoginUserId() 将返回 2001L
 * }
 * }</pre>
 */
public class UserContextTestHelper implements AutoCloseable {

    private final MockedStatic<UserContext> mocked;

    private UserContextTestHelper(Long userId) {
        mocked = mockStatic(UserContext.class);
        LoginUserContext ctx = new LoginUserContext();
        ctx.setUserId(userId);
        ctx.setUsername("testuser");
        ctx.setRoles(List.of("ROLE_USER"));

        mocked.when(UserContext::getLoginUserId).thenReturn(userId);
        mocked.when(UserContext::isLoggedIn).thenReturn(true);
        mocked.when(UserContext::getLoginUser).thenReturn(ctx);
        mocked.when(UserContext::isAdmin).thenReturn(false);
    }

    public static UserContextTestHelper withUserId(Long userId) {
        return new UserContextTestHelper(userId);
    }

    public static UserContextTestHelper withAdmin(Long userId) {
        UserContextTestHelper helper = new UserContextTestHelper(userId);
        LoginUserContext ctx = new LoginUserContext();
        ctx.setUserId(userId);
        ctx.setUsername("admin");
        ctx.setRoles(List.of("ROLE_ADMIN"));
        helper.mocked.when(UserContext::getLoginUser).thenReturn(ctx);
        helper.mocked.when(UserContext::isAdmin).thenReturn(true);
        return helper;
    }

    @Override
    public void close() {
        mocked.close();
    }
}
