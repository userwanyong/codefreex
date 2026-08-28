package cn.wanyj.codefreex.service.impl;

import cn.wanyj.auth.api.protobuf.AuthResult;
import cn.wanyj.auth.api.protobuf.LoginByCodeRpcResult;
import cn.wanyj.auth.api.protobuf.OAuthCallbackRpcResult;
import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.RegisterRpcResult;
import cn.wanyj.auth.api.protobuf.TokenRpcResponse;
import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import cn.wanyj.codefreex.auth.AuthConstants;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.OAuthLoginOutcome;
import cn.wanyj.codefreex.model.dto.request.LoginCodeRequest;
import cn.wanyj.codefreex.model.dto.request.OAuthCompleteRequest;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.request.UserAdminUpdateRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import cn.wanyj.codefreex.model.entity.InviteUser;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.AuthService;
import cn.wanyj.codefreex.service.InviteService;
import cn.wanyj.codefreex.service.UserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现：编排 auth-service RPC 与本地业务（邀请码、码点档案初始化）。
 * 用户身份（昵称/头像/状态/角色/密码）全部以 auth-service 为唯一数据源。
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthRpcClient authRpcClient;
    private final UserInfoService userInfoService;
    private final InviteService inviteService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String OAUTH_TEMP_KEY_PREFIX = "oauth:temp:";
    private static final long OAUTH_TEMP_EXPIRE_MINUTES = 10;

    /** 支持的 OAuth 提供方（与 auth-service LoginMethod 注册表保持一致） */
    private static final Set<String> SUPPORTED_OAUTH_PROVIDERS = Set.of("gitee", "github");

    /** 密码登录签发的 Access Token 有效期（秒） */
    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 1800;

    // ==================== 登录方式发现 ====================

    @Override
    public List<String> listLoginMethods() {
        return authRpcClient.listEnabledLoginMethods();
    }

    // ==================== 账号密码登录 ====================

    @Override
    public TokenResponse loginByPassword(String username, String password) {
        AuthResult authResult = authRpcClient.authenticate(username, password);
        if (!authResult.getSuccess()) {
            String message = StringUtils.isNotBlank(authResult.getMessage())
                    ? authResult.getMessage() : "账号或密码错误";
            throw new BusinessException(ResponseCode.PARAMS_ERROR, message);
        }
        prepareLoginContext(authResult.getUserId());
        TokenRpcResponse token = authRpcClient.generateToken(authResult.getUserId(), ACCESS_TOKEN_EXPIRE_SECONDS);
        return buildTokenResponse(token);
    }

    // ==================== 验证码登录 ====================

    @Override
    public void sendCode(String method, String target) {
        OperationResult operationResult = authRpcClient.sendCode(method, target);
        if (!operationResult.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, operationResult.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse loginByCode(LoginCodeRequest request) {
        String method = request.getMethod();
        String target = request.getTarget();

        // 新用户（目标不存在，将由 auth-service 自动注册）必须先通过邀请码校验
        LoginUserContext existing = authRpcClient.getUserByUsername(target);
        boolean isNewUser = existing == null;
        if (isNewUser) {
            if (StringUtils.isBlank(request.getInviteCode())) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "新用户登录需填写邀请码");
            }
            inviteService.validateInviteCode(request.getInviteCode());
        }

        LoginByCodeRpcResult result = authRpcClient.loginByCode(method, target, request.getCode());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    StringUtils.isNotBlank(result.getMessage()) ? result.getMessage() : "验证码错误或已过期");
        }

        UserRpcResponse loginUser = result.getUser();
        if (loginUser == null || loginUser.getId() <= 0) {
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "登录失败，获取用户信息失败");
        }

        if (isNewUser) {
            Long inviterId = consumeInvite(request.getInviteCode(), loginUser.getId());
            userInfoService.createUserInfo(loginUser.getId(), inviterId);
            // 验证码自动注册的账号密码为随机值，重置为默认密码供密码登录使用
            resetAutoRegisteredPassword(loginUser.getId());
        }

        prepareLoginContext(loginUser.getId());
        return buildTokenResponse(result.getToken());
    }

    // ==================== 邮箱注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse register(RegisterRequest request) {
        if (authRpcClient.getUserByUsername(request.getEmail()) != null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "该邮箱已注册");
        }

        // 1. 先验证邀请码（不消费，仅校验有效性）
        inviteService.validateInviteCode(request.getInviteCode());

        // 2. 在 auth-service 中注册用户（自动登录并签发令牌）
        String nickname = resolveEmailNickname(request.getEmail());
        RegisterRpcResult result = authRpcClient.register(
                request.getEmail(), request.getPassword(), request.getEmail(), nickname);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    StringUtils.isNotBlank(result.getMessage()) ? result.getMessage() : "注册失败");
        }

        UserRpcResponse registeredUser = result.hasUser() ? result.getUser() : null;
        if (registeredUser == null || registeredUser.getId() <= 0) {
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册用户失败");
        }

        // 3. 邀请码已验证通过，正式消费并创建本地业务档案
        Long inviterId = consumeInvite(request.getInviteCode(), registeredUser.getId());
        userInfoService.createUserInfo(registeredUser.getId(), inviterId);

        // 4. 构建登录上下文并返回令牌
        prepareLoginContext(registeredUser.getId());
        return buildTokenResponse(result.getToken());
    }

    // ==================== OAuth 登录 / 绑定 ====================

    @Override
    public String buildOAuthAuthorizeUrl(String provider) {
        String normalized = normalizeProvider(provider);
        String url = authRpcClient.buildOAuthAuthorizeUrl(normalized);
        if (StringUtils.isBlank(url)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    normalized + " 登录未启用或未配置凭证");
        }
        return url;
    }

    @Override
    public OAuthLoginOutcome handleOAuthCallback(String provider, String code, String state) {
        String normalized = normalizeProvider(provider);
        OAuthCallbackRpcResult result = authRpcClient.handleOAuthCallback(normalized, code, state);

        // 绑定流程回调：由个人中心发起的第三方账号绑定
        if (!result.getLogin()) {
            return OAuthLoginOutcome.ofBind(result.getSuccess(),
                    StringUtils.isNotBlank(result.getMessage()) ? result.getMessage() : "绑定操作失败");
        }

        if (result.getToken().getAccessToken().isEmpty()) {
            String message = StringUtils.isNotBlank(result.getMessage())
                    ? result.getMessage() : "OAuth 登录失败";
            return OAuthLoginOutcome.ofFailed(message);
        }

        UserRpcResponse oauthUser = result.getUser();
        if (oauthUser == null || oauthUser.getId() <= 0) {
            return OAuthLoginOutcome.ofFailed("获取用户信息失败");
        }
        long userId = oauthUser.getId();

        UserInfo localProfile = userInfoService.getUserInfo(userId);
        if (localProfile == null) {
            // 新用户：暂存到 Redis，等待提交邀请码完成注册
            String tempToken = UUID.randomUUID().toString().replace("-", "");
            stringRedisTemplate.opsForValue().set(OAUTH_TEMP_KEY_PREFIX + tempToken,
                    String.valueOf(userId), OAUTH_TEMP_EXPIRE_MINUTES, TimeUnit.MINUTES);
            return OAuthLoginOutcome.ofPending(tempToken,
                    StringUtils.isNotBlank(oauthUser.getNickname()) ? oauthUser.getNickname() : oauthUser.getUsername(),
                    StringUtils.isNotBlank(oauthUser.getAvatar()) ? oauthUser.getAvatar() : null);
        }

        prepareLoginContext(userId);
        return OAuthLoginOutcome.ofToken(buildTokenResponse(result.getToken()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenResponse completeOAuthRegistration(OAuthCompleteRequest request) {
        String tempKey = OAUTH_TEMP_KEY_PREFIX + request.getTempToken();
        String userIdStr = stringRedisTemplate.opsForValue().get(tempKey);
        if (userIdStr == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "临时令牌已过期，请重新登录");
        }
        long userId = Long.parseLong(userIdStr);

        LoginUserContext oauthUser = authRpcClient.getUserById(userId);
        if (oauthUser == null) {
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "用户不存在或已被删除");
        }

        // 1. 先验证邀请码（不消费，仅校验有效性）
        inviteService.validateInviteCode(request.getInviteCode());

        // 2. 邀请码验证通过，正式消费并创建本地业务档案
        Long inviterId = consumeInvite(request.getInviteCode(), userId);
        userInfoService.createUserInfo(userId, inviterId);
        // 第三方登录自动注册的账号密码为随机值，重置为默认密码供密码登录使用
        resetAutoRegisteredPassword(userId);
        stringRedisTemplate.delete(tempKey);

        // 3. 为已注册的 OAuth 用户补签令牌对
        prepareLoginContext(userId);
        TokenRpcResponse token = authRpcClient.generateToken(userId, ACCESS_TOKEN_EXPIRE_SECONDS);
        return buildTokenResponse(token);
    }

    // ==================== 令牌 ====================

    @Override
    public TokenResponse refresh(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "刷新令牌不能为空");
        }
        TokenRpcResponse token = authRpcClient.refreshToken(refreshToken);
        if (token == null || token.getAccessToken().isEmpty()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "刷新令牌无效或已过期，请重新登录");
        }
        return buildTokenResponse(token);
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (StringUtils.isNotBlank(accessToken) || StringUtils.isNotBlank(refreshToken)) {
            authRpcClient.logout(accessToken, refreshToken);
        }
        UserContext.removeLoginUser();
    }

    @Override
    public LoginUserContext getLoginUser() {
        LoginUserContext current = UserContext.getLoginUser();
        if (current == null) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR);
        }
        LoginUserContext full = authRpcClient.getUserById(current.getUserId());
        if (full == null) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR);
        }
        return full;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 登录/注册成功后的统一上下文处理：
     * 创建本地业务档案（仅首次）并写入当前请求用户上下文；
     * 用户被禁用/删除时 getUserById 返回 null，直接拒绝登录。
     */
    private LoginUserContext prepareLoginContext(long userId) {
        LoginUserContext userContext = authRpcClient.getUserById(userId);
        if (userContext == null) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "账号已被禁用或不存在");
        }

        if (userInfoService.getUserInfo(userId) == null) {
            userInfoService.createUserInfo(userId, null);
        }

        UserContext.setLoginUser(userContext);
        return userContext;
    }

    /**
     * 自动注册（第三方/验证码）账号重置为默认密码。
     * auth-service 生成的随机密码无法用于密码登录，重置后用户可凭默认密码登录并在个人中心修改。
     * 失败仅记录日志，不阻断登录主流程。
     */
    private void resetAutoRegisteredPassword(long userId) {
        try {
            UserAdminUpdateRequest update = new UserAdminUpdateRequest();
            update.setUserId(userId);
            update.setPassword(AuthConstants.AUTO_REGISTERED_DEFAULT_PASSWORD);
            OperationResult result = authRpcClient.updateUser(update);
            if (!result.getSuccess()) {
                log.warn("重置自动注册账号默认密码失败: userId={}, message={}", userId, result.getMessage());
            }
        } catch (Exception e) {
            log.warn("重置自动注册账号默认密码异常: userId={}", userId, e);
        }
    }

    /**
     * 消费邀请码并返回邀请人 ID（消费失败抛出业务异常）
     */
    private Long consumeInvite(String inviteCode, long inviteeId) {
        try {
            inviteService.useInviteCode(inviteCode, inviteeId);
            InviteUser inviterRecord = inviteService.getInviter(inviteeId);
            return inviterRecord != null ? inviterRecord.getInviterId() : null;
        } catch (Exception e) {
            log.error("邀请码消费失败, inviteCode={}, inviteeId={}", inviteCode, inviteeId, e);
            throw e;
        }
    }

    private TokenResponse buildTokenResponse(TokenRpcResponse token) {
        TokenResponse response = new TokenResponse();
        response.setAccessToken(token.getAccessToken());
        response.setRefreshToken(token.getRefreshToken());
        response.setExpiresIn(token.getExpiresIn());
        response.setTokenType("Bearer");
        return response;
    }

    private String normalizeProvider(String provider) {
        if (StringUtils.isBlank(provider)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "OAuth 提供方不能为空");
        }
        String normalized = provider.trim().toLowerCase();
        if (!SUPPORTED_OAUTH_PROVIDERS.contains(normalized)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "不支持的 OAuth 提供方: " + provider);
        }
        return normalized;
    }

    private String resolveEmailNickname(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
