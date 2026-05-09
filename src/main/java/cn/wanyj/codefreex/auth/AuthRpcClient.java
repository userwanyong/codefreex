package cn.wanyj.codefreex.auth;

import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.auth.api.protobuf.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 认证服务 RPC 客户端封装
 * @author wanyj
 */
@Slf4j
@Component
public class AuthRpcClient {

    @Value("${auth-service.tenant-id:5}")
    private long tenantId;

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 5000)
    private AuthRpcServiceProtobuf authRpcService;

    @DubboReference(version = "1.0.0", protocol = "tri", timeout = 3000)
    private TokenRpcServiceProtobuf tokenRpcService;

    /**
     * 用户注册（自动登录）
     */
    public RegisterRpcResult register(String username, String password, String email, Long tenantId) {
        return register(username, password, email, tenantId, null);
    }

    /**
     * 用户注册（自动登录，带昵称）
     */
    public RegisterRpcResult register(String username, String password, String email, Long tenantId, String nickname) {
        RegisterRpcRequest.Builder builder = RegisterRpcRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setTenantId(String.valueOf(tenantId != null ? tenantId : this.tenantId));
        if (email != null) {
            builder.setEmail(email);
        }
        if (nickname != null && !nickname.isEmpty()) {
            builder.setNickname(nickname);
        }
        return authRpcService.register(builder.build());
    }

    /**
     * 用户登录
     */
    public AuthResult authenticate(String username, String password, Long tenantId) {
        LoginRpcRequest request = LoginRpcRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setTenantId(String.valueOf(tenantId != null ? tenantId : this.tenantId))
                .build();
        return authRpcService.authenticate(request);
    }

//    /**
//     * 用户登出
//     */
//    public OperationResult logout(Long userId, String accessToken) {
//        LogoutRequest request = LogoutRequest.newBuilder()
//                .setUserId(userId)
//                .setAccessToken(accessToken != null ? accessToken : "")
//                .build();
//        return tokenRpcService.logout(request);
//    }

    /**
     * 根据 ID 获取用户信息
     */
    public LoginUserContext getUserById(Long userId) {
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantId(String.valueOf(tenantId))
                .build();
        UserRpcResponse user = authRpcService.getUserById(request);
        if (user == null || user.getId() == 0) {
            return null;
        }
        return toLoginUserContext(user);
    }

    /**
     * 根据用户名获取用户信息
     */
    public LoginUserContext getUserByUsername(String username) {
        UserByUsernameRequest request = UserByUsernameRequest.newBuilder()
                .setUsername(username)
                .setTenantId(String.valueOf(tenantId))
                .build();
        UserRpcResponse user = authRpcService.getUserByUsername(request);
        if (user == null || user.getId() == 0) {
            return null;
        }
        return toLoginUserContext(user);
    }

    /**
     * 获取用户角色列表
     */
    public List<String> getUserRoles(Long userId) {
        try {
            UserRolesRequest request = UserRolesRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantId(String.valueOf(tenantId))
                    .build();
            StringListResponse response = authRpcService.getUserRoles(request);
            return response.getValuesList();
        } catch (Exception e) {
            log.warn("获取用户角色失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户权限列表
     */
    public List<String> getUserPermissions(Long userId) {
        try {
            UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantId(String.valueOf(tenantId))
                    .build();
            StringListResponse response = authRpcService.getUserPermissions(request);
            return response.getValuesList();
        } catch (Exception e) {
            log.warn("获取用户权限失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

//    /**
//     * 修改密码
//     */
//    public OperationResult changePassword(Long userId, String oldPassword, String newPassword) {
//        ChangePasswordRequest request = ChangePasswordRequest.newBuilder()
//                .setUserId(userId)
//                .setTenantId(tenantId)
//                .setOldPassword(oldPassword)
//                .setNewPassword(newPassword)
//                .build();
//        return authRpcService.changePassword(request);
//    }

    /**
     * 为用户生成令牌
     */
    public String generateToken(Long userId, long expirationSeconds) {
        TokenGenerationRequest request = TokenGenerationRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setExpiration(expirationSeconds)
                .build();
        TokenRpcResponse response = tokenRpcService.generateToken(request);
        return response != null ? response.getAccessToken() : null;
    }

    private LoginUserContext toLoginUserContext(UserRpcResponse user) {
        LoginUserContext ctx = new LoginUserContext();
        ctx.setUserId(user.getId());
        ctx.setUsername(user.getUsername());
        ctx.setEmail(user.getEmail());
        ctx.setPhone(user.getPhone());
        ctx.setNickname(user.getNickname());
        ctx.setAvatar(user.getAvatar());
        ctx.setRoles(new ArrayList<>(user.getRolesList()));
        ctx.setPermissions(new ArrayList<>(user.getPermissionsList()));
        return ctx;
    }
}
