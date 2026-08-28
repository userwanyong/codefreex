package cn.wanyj.codefreex.auth;

import cn.wanyj.auth.api.protobuf.AssignPermissionsRpcRequest;
import cn.wanyj.auth.api.protobuf.AssignRolesRpcRequest;
import cn.wanyj.auth.api.protobuf.AuthResult;
import cn.wanyj.auth.api.protobuf.AuthRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.BindContactRpcRequest;
import cn.wanyj.auth.api.protobuf.ChangePasswordRpcRequest;
import cn.wanyj.auth.api.protobuf.ContactBindingRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.ContactUnbindRpcRequest;
import cn.wanyj.auth.api.protobuf.CreatePermissionRpcRequest;
import cn.wanyj.auth.api.protobuf.CreateRoleRpcRequest;
import cn.wanyj.auth.api.protobuf.DeletePermissionRpcRequest;
import cn.wanyj.auth.api.protobuf.DeleteRoleRpcRequest;
import cn.wanyj.auth.api.protobuf.DeleteUserRpcRequest;
import cn.wanyj.auth.api.protobuf.EnabledLoginMethodsRpcRequest;
import cn.wanyj.auth.api.protobuf.GetAllPermissionsRequest;
import cn.wanyj.auth.api.protobuf.GetAllRolesRequest;
import cn.wanyj.auth.api.protobuf.LoginByCodeRpcRequest;
import cn.wanyj.auth.api.protobuf.LoginByCodeRpcResult;
import cn.wanyj.auth.api.protobuf.LoginMethodListRpcResponse;
import cn.wanyj.auth.api.protobuf.LoginMethodRpcResponse;
import cn.wanyj.auth.api.protobuf.LoginMethodRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.LoginRpcRequest;
import cn.wanyj.auth.api.protobuf.LogoutRpcRequest;
import cn.wanyj.auth.api.protobuf.OAuthAuthorizeUrlRpcRequest;
import cn.wanyj.auth.api.protobuf.OAuthBindingListRpcResponse;
import cn.wanyj.auth.api.protobuf.OAuthBindingRpcResponse;
import cn.wanyj.auth.api.protobuf.OAuthBindingsRpcRequest;
import cn.wanyj.auth.api.protobuf.OAuthBindUrlRpcRequest;
import cn.wanyj.auth.api.protobuf.OAuthCallbackRpcRequest;
import cn.wanyj.auth.api.protobuf.OAuthCallbackRpcResult;
import cn.wanyj.auth.api.protobuf.OAuthRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.OAuthUrlRpcResponse;
import cn.wanyj.auth.api.protobuf.OAuthUnbindRpcRequest;
import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.OssRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.ParseTokenRpcRequest;
import cn.wanyj.auth.api.protobuf.PermissionListResponse;
import cn.wanyj.auth.api.protobuf.PermissionRpcResponse;
import cn.wanyj.auth.api.protobuf.PermissionRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.RefreshTokenRpcRequest;
import cn.wanyj.auth.api.protobuf.RegisterRpcRequest;
import cn.wanyj.auth.api.protobuf.RegisterRpcResult;
import cn.wanyj.auth.api.protobuf.RevokeAllTokensRpcRequest;
import cn.wanyj.auth.api.protobuf.RoleListResponse;
import cn.wanyj.auth.api.protobuf.RoleRpcResponse;
import cn.wanyj.auth.api.protobuf.RoleRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.SaveTenantLoginMethodRpcRequest;
import cn.wanyj.auth.api.protobuf.SearchUsersRequest;
import cn.wanyj.auth.api.protobuf.SendCodeRpcRequest;
import cn.wanyj.auth.api.protobuf.StringListResponse;
import cn.wanyj.auth.api.protobuf.TenantLoginMethodRpcRequest;
import cn.wanyj.auth.api.protobuf.TokenGenerationRequest;
import cn.wanyj.auth.api.protobuf.TokenRpcResponse;
import cn.wanyj.auth.api.protobuf.TokenRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.TokenValidationResult;
import cn.wanyj.auth.api.protobuf.UpdateRoleRpcRequest;
import cn.wanyj.auth.api.protobuf.UpdateUserRpcRequest;
import cn.wanyj.auth.api.protobuf.UpdateUserStatusRpcRequest;
import cn.wanyj.auth.api.protobuf.UploadAvatarRpcRequest;
import cn.wanyj.auth.api.protobuf.UploadAvatarRpcResponse;
import cn.wanyj.auth.api.protobuf.UserByIdRequest;
import cn.wanyj.auth.api.protobuf.UserByUsernameRequest;
import cn.wanyj.auth.api.protobuf.UserPageResponse;
import cn.wanyj.auth.api.protobuf.UserPermissionsRequest;
import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import cn.wanyj.auth.api.protobuf.UserRpcServiceProtobuf;
import cn.wanyj.auth.api.protobuf.UserRolesRequest;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.UserAdminUpdateRequest;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证授权服务（auth-service）RPC 客户端封装。
 * <p>
 * 集成约定见 auth-service 的 docs/ai-rpc-integration.md：
 * 租户一律传 tenantUid（对外随机串），业务 ID 传数字字符串；
 * 每次调用前在 attachment 中携带 rpc-service-token；
 * 业务失败不抛异常、按响应字段判断，仅网络层异常为 RpcException；
 * 写操作 retries=0 防止重复执行。
 *
 * @author wanyj
 */
@Slf4j
@Component
public class AuthRpcClient {

    private static final String RPC_TOKEN_ATTACHMENT = "rpc-service-token";

    @Value("${auth-service.tenant-uid}")
    private String tenantUid;

    @Value("${auth-service.rpc-token:}")
    private String rpcToken;

    @Value("${auth-service.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 0)
    private AuthRpcServiceProtobuf authRpcService;

    @DubboReference(version = "1.0.0", timeout = 3000, retries = 1)
    private TokenRpcServiceProtobuf tokenRpcService;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 0)
    private OAuthRpcServiceProtobuf oauthRpcService;

    @DubboReference(version = "1.0.0", timeout = 3000, retries = 1)
    private LoginMethodRpcServiceProtobuf loginMethodRpcService;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 0)
    private UserRpcServiceProtobuf userRpcService;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 1)
    private RoleRpcServiceProtobuf roleRpcService;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 1)
    private PermissionRpcServiceProtobuf permissionRpcService;

    @DubboReference(version = "1.0.0", timeout = 5000, retries = 0)
    private ContactBindingRpcServiceProtobuf contactBindingRpcService;

    @DubboReference(version = "1.0.0", timeout = 15000, retries = 0)
    private OssRpcServiceProtobuf ossRpcService;

    public String getTenantUid() {
        return tenantUid;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    /**
     * 每次调用前设置服务间鉴权 attachment（服务端未配置 token 时自动跳过校验）
     */
    private void attachRpcToken() {
        if (rpcToken != null && !rpcToken.isBlank()) {
            RpcContext.getClientAttachment().setAttachment(RPC_TOKEN_ATTACHMENT, rpcToken);
        }
    }

    /**
     * 统一包装 RPC 网络层异常：仅网络/超时/鉴权失败会抛到这里
     */
    private <T> T invoke(String action, RpcCall<T> call) {
        attachRpcToken();
        try {
            return call.get();
        } catch (RpcException e) {
            log.error("auth-service RPC 调用失败: action={}, message={}", action, e.getMessage());
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后重试");
        }
    }

    @FunctionalInterface
    private interface RpcCall<T> {
        T get();
    }

    // ==================== 登录方式发现 ====================

    /**
     * 查询本租户当前开放的登录方式（如 password / email:smtp / oauth:gitee），
     * 管理端开闭某种方式后此处实时同步。失败返回仅含 password 的默认列表。
     */
    public List<String> listEnabledLoginMethods() {
        try {
            EnabledLoginMethodsRpcRequest request = EnabledLoginMethodsRpcRequest.newBuilder()
                    .setTenantUid(tenantUid)
                    .build();
            StringListResponse response = invoke("listEnabledMethods",
                    () -> loginMethodRpcService.listEnabledMethods(request));
            if (response == null || response.getValuesList().isEmpty()) {
                return Collections.singletonList("password");
            }
            return new ArrayList<>(response.getValuesList());
        } catch (BusinessException e) {
            // 登录方式发现属于公开接口，失败时兜底返回 password，不阻断登录页
            log.warn("查询开放登录方式失败，兜底返回 password: {}", e.getMessage());
            return Collections.singletonList("password");
        }
    }

    // ==================== 注册 / 密码登录 ====================

    /**
     * 注册用户（auth-service 自动分配 ROLE_USER 并签发令牌）
     */
    public RegisterRpcResult register(String username, String password, String email, String nickname) {
        RegisterRpcRequest.Builder builder = RegisterRpcRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setTenantUid(tenantUid);
        if (email != null && !email.isBlank()) {
            builder.setEmail(email);
        }
        if (nickname != null && !nickname.isBlank()) {
            builder.setNickname(nickname);
        }
        RegisterRpcRequest request = builder.build();
        return invoke("register", () -> authRpcService.register(request));
    }

    /**
     * 账号密码登录校验（用户名或邮箱），只做校验不返回令牌
     */
    public AuthResult authenticate(String username, String password) {
        LoginRpcRequest request = LoginRpcRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setTenantUid(tenantUid)
                .build();
        return invoke("authenticate", () -> authRpcService.authenticate(request));
    }

    // ==================== 验证码登录 ====================

    /**
     * 发送邮箱/短信验证码
     */
    public OperationResult sendCode(String method, String target) {
        SendCodeRpcRequest request = SendCodeRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setMethod(method)
                .setTarget(target)
                .build();
        return invoke("sendCode", () -> authRpcService.sendCode(request));
    }

    /**
     * 验证码登录（target 在租户内不存在时由 auth-service 自动注册）
     */
    public LoginByCodeRpcResult loginByCode(String method, String target, String code) {
        LoginByCodeRpcRequest request = LoginByCodeRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setMethod(method)
                .setTarget(target)
                .setCode(code)
                .build();
        return invoke("loginByCode", () -> authRpcService.loginByCode(request));
    }

    // ==================== OAuth ====================

    /**
     * 构建 OAuth 授权页 URL（state 已由 auth-service 暂存，10 分钟一次性）
     */
    public String buildOAuthAuthorizeUrl(String provider) {
        OAuthAuthorizeUrlRpcRequest request = OAuthAuthorizeUrlRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setProvider(provider)
                .build();
        OAuthUrlRpcResponse response = invoke("buildAuthorizeUrl",
                () -> oauthRpcService.buildAuthorizeUrl(request));
        return response != null ? response.getUrl() : "";
    }

    /**
     * 处理 OAuth 回调：校验 state + 换 token + 匹配/建用户 + 签发令牌
     */
    public OAuthCallbackRpcResult handleOAuthCallback(String provider, String code, String state) {
        OAuthCallbackRpcRequest request = OAuthCallbackRpcRequest.newBuilder()
                .setProvider(provider)
                .setCode(code)
                .setState(state)
                .build();
        return invoke("handleCallback", () -> oauthRpcService.handleCallback(request));
    }

    // ==================== 令牌 ====================

    /**
     * 解析 Access Token（网关鉴权入口）：校验签名、有效期与黑名单
     */
    public TokenValidationResult parseToken(String accessToken) {
        ParseTokenRpcRequest request = ParseTokenRpcRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();
        return invoke("parseToken", () -> tokenRpcService.parseToken(request));
    }

    /**
     * 为已认证用户签发令牌对（expiration 单位秒，<=0 用服务端默认）
     */
    public TokenRpcResponse generateToken(long userId, long expirationSeconds) {
        TokenGenerationRequest request = TokenGenerationRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .setExpiration(expirationSeconds)
                .build();
        return invoke("generateToken", () -> tokenRpcService.generateToken(request));
    }

    /**
     * 刷新令牌（成功轮换返回新令牌对，旧的立即失效）
     */
    public TokenRpcResponse refreshToken(String refreshToken) {
        RefreshTokenRpcRequest request = RefreshTokenRpcRequest.newBuilder()
                .setRefreshToken(refreshToken)
                .build();
        return invoke("refreshToken", () -> authRpcService.refreshToken(request));
    }

    /**
     * 登出（拉黑 access + 删除 refresh）
     */
    public OperationResult logout(String accessToken, String refreshToken) {
        LogoutRpcRequest request = LogoutRpcRequest.newBuilder()
                .setAccessToken(accessToken != null ? accessToken : "")
                .setRefreshToken(refreshToken != null ? refreshToken : "")
                .build();
        return invoke("logout", () -> authRpcService.logout(request));
    }

    // ==================== 用户查询 ====================

    /**
     * 根据 ID 获取用户（不存在/跨租户/禁用时返回 null）
     */
    public LoginUserContext getUserById(long userId) {
        return toLoginUserContext(getUserRpcById(userId));
    }

    /**
     * 根据 ID 获取用户原始 RPC 响应（禁用用户不可见，返回空对象）
     */
    public UserRpcResponse getUserRpcById(long userId) {
        UserByIdRequest request = UserByIdRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("getUserById", () -> authRpcService.getUserById(request));
    }

    /**
     * 管理端按 ID 定位用户（含禁用用户）：
     * getUserById 对禁用用户不可见，命中不到时退化为检索定位。
     */
    public UserRpcResponse findUserForAdmin(long userId) {
        UserRpcResponse user = getUserRpcById(userId);
        if (user != null && user.getId() != 0) {
            return user;
        }
        UserPageResponse page = searchUsers("", 1, 200);
        for (UserRpcResponse item : page.getItemsList()) {
            if (item.getId() == userId) {
                return item;
            }
        }
        return UserRpcResponse.getDefaultInstance();
    }

    /**
     * 根据用户名/邮箱获取用户（不存在/跨租户/禁用时返回 null）
     */
    public LoginUserContext getUserByUsername(String username) {
        UserByUsernameRequest request = UserByUsernameRequest.newBuilder()
                .setUsername(username)
                .setTenantUid(tenantUid)
                .build();
        UserRpcResponse user = invoke("getUserByUsername", () -> authRpcService.getUserByUsername(request));
        return toLoginUserContext(user);
    }

    /**
     * 获取用户角色列表
     */
    public List<String> getUserRoles(long userId) {
        try {
            UserRolesRequest request = UserRolesRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantUid(tenantUid)
                    .build();
            StringListResponse response = invoke("getUserRoles", () -> authRpcService.getUserRoles(request));
            return response != null ? new ArrayList<>(response.getValuesList()) : Collections.emptyList();
        } catch (BusinessException e) {
            log.warn("获取用户角色失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取用户权限列表
     */
    public List<String> getUserPermissions(long userId) {
        try {
            UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantUid(tenantUid)
                    .build();
            StringListResponse response = invoke("getUserPermissions",
                    () -> authRpcService.getUserPermissions(request));
            return response != null ? new ArrayList<>(response.getValuesList()) : Collections.emptyList();
        } catch (BusinessException e) {
            log.warn("获取用户权限失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private LoginUserContext toLoginUserContext(UserRpcResponse user) {
        if (user == null || user.getId() == 0) {
            return null;
        }
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

    /**
     * 批量获取用户（逐个调用，单个失败跳过不影响其他用户）
     */
    public Map<Long, LoginUserContext> batchGetUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LoginUserContext> result = new HashMap<>();
        for (Long userId : userIds) {
            try {
                LoginUserContext user = getUserById(userId);
                if (user != null) {
                    result.put(userId, user);
                }
            } catch (BusinessException e) {
                log.warn("批量获取用户失败: userId={}, message={}", userId, e.getMessage());
            }
        }
        return result;
    }

    // ==================== 用户管理（管理端） ====================

    /**
     * 分页搜索用户（keyword 匹配账号或邮箱，创建时间倒序）
     */
    public UserPageResponse searchUsers(String keyword, int page, int size) {
        SearchUsersRequest request = SearchUsersRequest.newBuilder()
                .setKeyword(keyword != null ? keyword : "")
                .setTenantUid(tenantUid)
                .setPage(page)
                .setSize(size)
                .build();
        return invoke("searchUsers", () -> authRpcService.searchUsers(request));
    }

    /**
     * 更新用户（fields_to_update 掩码由非空字段自动生成；邮箱/手机传空串表示清空）
     */
    public OperationResult updateUser(UserAdminUpdateRequest update) {
        UpdateUserRpcRequest.Builder builder = UpdateUserRpcRequest.newBuilder()
                .setUserId(String.valueOf(update.getUserId()))
                .setTenantUid(tenantUid);
        if (update.getUsername() != null) {
            builder.addFieldsToUpdate("username").setUsername(update.getUsername());
        }
        if (update.getPassword() != null && !update.getPassword().isBlank()) {
            builder.addFieldsToUpdate("password").setPassword(update.getPassword());
        }
        if (update.getEmail() != null) {
            builder.addFieldsToUpdate("email").setEmail(update.getEmail());
        }
        if (update.getPhone() != null) {
            builder.addFieldsToUpdate("phone").setPhone(update.getPhone());
        }
        if (update.getNickname() != null) {
            builder.addFieldsToUpdate("nickname").setNickname(update.getNickname());
        }
        if (update.getAvatar() != null) {
            builder.addFieldsToUpdate("avatar").setAvatar(update.getAvatar());
        }
        if (update.getStatus() != null) {
            builder.addFieldsToUpdate("status").setStatus(update.getStatus());
        }
        UpdateUserRpcRequest request = builder.build();
        return invoke("updateUser", () -> userRpcService.updateUser(request));
    }

    /**
     * 更新用户状态（1-正常，0-禁用）
     */
    public OperationResult updateUserStatus(long userId, int status) {
        UpdateUserStatusRpcRequest request = UpdateUserStatusRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .setStatus(status)
                .build();
        return invoke("updateUserStatus", () -> userRpcService.updateUserStatus(request));
    }

    /**
     * 分配用户角色（全量替换）
     */
    public OperationResult assignRoles(long userId, List<Long> roleIds) {
        AssignRolesRpcRequest.Builder builder = AssignRolesRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid);
        if (roleIds != null) {
            roleIds.forEach(roleId -> builder.addRoleIds(String.valueOf(roleId)));
        }
        AssignRolesRpcRequest request = builder.build();
        return invoke("assignRoles", () -> userRpcService.assignRoles(request));
    }

    /**
     * 删除用户（auth-service 物理删除，含角色关联与 OAuth 绑定）
     */
    public OperationResult deleteUser(long userId) {
        DeleteUserRpcRequest request = DeleteUserRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("deleteUser", () -> userRpcService.deleteUser(request));
    }

    /**
     * 吊销用户全部令牌（禁用/删除用户后立即踢下线）
     */
    public void revokeAllTokens(long userId) {
        try {
            RevokeAllTokensRpcRequest request = RevokeAllTokensRpcRequest.newBuilder()
                    .setUserId(String.valueOf(userId))
                    .setTenantUid(tenantUid)
                    .build();
            attachRpcToken();
            tokenRpcService.revokeAllTokens(request);
        } catch (RpcException e) {
            log.warn("吊销用户令牌失败: userId={}, message={}", userId, e.getMessage());
        }
    }

    // ==================== 角色管理 ====================

    /**
     * 获取本租户全部角色（含权限 code 列表）
     */
    public List<RoleRpcResponse> getAllRoles() {
        GetAllRolesRequest request = GetAllRolesRequest.newBuilder()
                .setTenantUid(tenantUid)
                .build();
        RoleListResponse response = invoke("getAllRoles", () -> roleRpcService.getAllRoles(request));
        return response != null ? new ArrayList<>(response.getRolesList()) : Collections.emptyList();
    }

    /**
     * 创建角色（code 租户内不可重复）
     */
    public RoleRpcResponse createRole(String code, String name, String description) {
        CreateRoleRpcRequest request = CreateRoleRpcRequest.newBuilder()
                .setCode(code)
                .setName(name)
                .setDescription(description != null ? description : "")
                .setTenantUid(tenantUid)
                .build();
        return invoke("createRole", () -> roleRpcService.createRole(request));
    }

    /**
     * 更新角色（仅名称与描述，code 不可改）
     */
    public OperationResult updateRole(long roleId, String name, String description) {
        UpdateRoleRpcRequest request = UpdateRoleRpcRequest.newBuilder()
                .setRoleId(String.valueOf(roleId))
                .setName(name)
                .setDescription(description != null ? description : "")
                .setTenantUid(tenantUid)
                .build();
        return invoke("updateRole", () -> roleRpcService.updateRole(request));
    }

    /**
     * 删除角色
     */
    public OperationResult deleteRole(long roleId) {
        DeleteRoleRpcRequest request = DeleteRoleRpcRequest.newBuilder()
                .setRoleId(String.valueOf(roleId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("deleteRole", () -> roleRpcService.deleteRole(request));
    }

    /**
     * 为角色分配权限（全量替换）
     */
    public OperationResult assignRolePermissions(long roleId, List<Long> permissionIds) {
        AssignPermissionsRpcRequest.Builder builder = AssignPermissionsRpcRequest.newBuilder()
                .setRoleId(String.valueOf(roleId))
                .setTenantUid(tenantUid);
        if (permissionIds != null) {
            permissionIds.forEach(permissionId -> builder.addPermissionIds(String.valueOf(permissionId)));
        }
        AssignPermissionsRpcRequest request = builder.build();
        return invoke("assignPermissions", () -> roleRpcService.assignPermissions(request));
    }

    // ==================== 权限管理 ====================

    /**
     * 获取本租户全部权限
     */
    public List<PermissionRpcResponse> getAllPermissions() {
        GetAllPermissionsRequest request = GetAllPermissionsRequest.newBuilder()
                .setTenantUid(tenantUid)
                .build();
        PermissionListResponse response = invoke("getAllPermissions",
                () -> permissionRpcService.getAllPermissions(request));
        return response != null ? new ArrayList<>(response.getPermissionsList()) : Collections.emptyList();
    }

    /**
     * 创建权限（code 租户内不可重复）
     */
    public PermissionRpcResponse createPermission(String code, String name, String resource,
                                                  String action, String description) {
        CreatePermissionRpcRequest request = CreatePermissionRpcRequest.newBuilder()
                .setCode(code)
                .setName(name)
                .setResource(resource != null ? resource : "")
                .setAction(action != null ? action : "")
                .setDescription(description != null ? description : "")
                .setTenantUid(tenantUid)
                .build();
        return invoke("createPermission", () -> permissionRpcService.createPermission(request));
    }

    /**
     * 删除权限（同时解除角色关联）
     */
    public OperationResult deletePermission(long permissionId) {
        DeletePermissionRpcRequest request = DeletePermissionRpcRequest.newBuilder()
                .setPermissionId(String.valueOf(permissionId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("deletePermission", () -> permissionRpcService.deletePermission(request));
    }

    // ==================== 登录方式配置（租户级，管理端开闭实时生效） ====================

    /**
     * 列出本租户可配置的登录方式及开关/凭证来源
     */
    public List<LoginMethodRpcResponse> listTenantLoginMethods() {
        TenantLoginMethodRpcRequest request = TenantLoginMethodRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .build();
        LoginMethodListRpcResponse response = invoke("listTenantConfigs",
                () -> loginMethodRpcService.listTenantConfigs(request));
        return response != null ? new ArrayList<>(response.getItemsList()) : Collections.emptyList();
    }

    /**
     * 保存本租户登录方式开关与凭证来源
     */
    public OperationResult saveTenantLoginMethod(String method, int enabled, int usePlatformConfig,
                                                 String configJson) {
        SaveTenantLoginMethodRpcRequest request = SaveTenantLoginMethodRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setMethod(method)
                .setEnabled(enabled)
                .setUsePlatformConfig(usePlatformConfig)
                .setConfigJson(configJson != null ? configJson : "")
                .build();
        return invoke("saveTenantConfig", () -> loginMethodRpcService.saveTenantConfig(request));
    }

    // ==================== 账号绑定 ====================

    /**
     * 绑定/换绑邮箱（须先向该邮箱发送验证码）
     */
    public OperationResult bindEmail(long userId, String method, String target, String code) {
        BindContactRpcRequest request = BindContactRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .setMethod(method)
                .setTarget(target)
                .setCode(code)
                .build();
        return invoke("bindEmail", () -> contactBindingRpcService.bindEmail(request));
    }

    /**
     * 解绑邮箱
     */
    public OperationResult unbindEmail(long userId) {
        ContactUnbindRpcRequest request = ContactUnbindRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("unbindEmail", () -> contactBindingRpcService.unbindEmail(request));
    }

    /**
     * 绑定/换绑手机号（须先向该手机号发送验证码）
     */
    public OperationResult bindPhone(long userId, String method, String target, String code) {
        BindContactRpcRequest request = BindContactRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .setMethod(method)
                .setTarget(target)
                .setCode(code)
                .build();
        return invoke("bindPhone", () -> contactBindingRpcService.bindPhone(request));
    }

    /**
     * 解绑手机号
     */
    public OperationResult unbindPhone(long userId) {
        ContactUnbindRpcRequest request = ContactUnbindRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .build();
        return invoke("unbindPhone", () -> contactBindingRpcService.unbindPhone(request));
    }

    /**
     * 列出用户已绑定的第三方平台
     */
    public List<OAuthBindingRpcResponse> listOAuthBindings(long userId) {
        OAuthBindingsRpcRequest request = OAuthBindingsRpcRequest.newBuilder()
                        .setTenantUid(tenantUid)
                        .setUserId(String.valueOf(userId))
                        .build();
        OAuthBindingListRpcResponse response = invoke("listBindings",
                () -> oauthRpcService.listBindings(request));
        return response != null ? new ArrayList<>(response.getBindingsList()) : Collections.emptyList();
    }

    /**
     * 解绑第三方平台
     */
    public OperationResult unbindOAuth(long userId, String provider) {
        OAuthUnbindRpcRequest request = OAuthUnbindRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setUserId(String.valueOf(userId))
                .setProvider(provider)
                .build();
        return invoke("unbind", () -> oauthRpcService.unbind(request));
    }

    /**
     * 构建第三方账号绑定授权页 URL（已登录用户发起绑定）
     */
    public String buildOAuthBindUrl(long userId, String provider) {
        OAuthBindUrlRpcRequest request = OAuthBindUrlRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setUserId(String.valueOf(userId))
                .setProvider(provider)
                .build();
        OAuthUrlRpcResponse response = invoke("buildBindAuthorizeUrl",
                () -> oauthRpcService.buildBindAuthorizeUrl(request));
        return response != null ? response.getUrl() : "";
    }

    // ==================== 头像上传（存入 auth-service 对象存储） ====================

    /**
     * 上传头像到 auth-service OSS，返回可访问 URL
     */
    public String uploadAvatar(long userId, String filename, String contentType, byte[] data) {
        UploadAvatarRpcRequest request = UploadAvatarRpcRequest.newBuilder()
                .setTenantUid(tenantUid)
                .setUserId(String.valueOf(userId))
                .setFilename(filename != null ? filename : "avatar")
                .setContentType(contentType != null ? contentType : "")
                .setData(ByteString.copyFrom(data))
                .build();
        UploadAvatarRpcResponse response = invoke("uploadAvatar", () -> ossRpcService.uploadAvatar(request));
        return response != null ? response.getUrl() : "";
    }

    // ==================== 修改密码 ====================

    /**
     * 修改自己的密码（需校验旧密码）
     */
    public OperationResult changePassword(long userId, String oldPassword, String newPassword) {
        ChangePasswordRpcRequest request = ChangePasswordRpcRequest.newBuilder()
                .setUserId(String.valueOf(userId))
                .setTenantUid(tenantUid)
                .setOldPassword(oldPassword)
                .setNewPassword(newPassword)
                .build();
        return invoke("changePassword", () -> authRpcService.changePassword(request));
    }
}
