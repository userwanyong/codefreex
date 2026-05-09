package cn.wanyj.codefreex.service.impl;

import cn.authing.sdk.java.client.AuthenticationClient;
import cn.authing.sdk.java.dto.*;
import cn.authing.sdk.java.dto.authentication.UserInfo;
import cn.hutool.core.util.IdUtil;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.config.AuthingConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.request.WechatCompleteRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import cn.wanyj.codefreex.model.dto.response.WechatLoginResponse;
import cn.wanyj.codefreex.model.dto.response.WechatQrCodeResponse;
import cn.wanyj.codefreex.service.AuthService;
import cn.wanyj.codefreex.service.InviteService;
import cn.wanyj.codefreex.service.UserInfoService;
import cn.wanyj.auth.api.protobuf.AuthResult;
import cn.wanyj.auth.api.protobuf.RegisterRpcResult;
import cn.wanyj.auth.api.protobuf.TokenRpcResponse;
import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthRpcClient authRpcClient;
    private final AuthingConfig authingConfig;
    private final UserInfoService userInfoService;
    private final InviteService inviteService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String WECHAT_TEMP_KEY_PREFIX = "wechat:temp:";
    private static final long WECHAT_TEMP_EXPIRE_MINUTES = 10;

    // ==================== 邮箱验证码 ====================

    @Override
    public void sendEmailCode(String email) {
        LoginUserContext existing = authRpcClient.getUserByUsername(email);
        if (existing != null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "该邮箱已注册");
        }

        try {
            AuthenticationClient client = authingConfig.newAuthenticationClient();
            SendEmailDto request = new SendEmailDto();
            request.setEmail(email);
            request.setChannel(SendEmailDto.Channel.CHANNEL_REGISTER);

            SendEmailRespDto response = client.sendEmail(request);
            ensureAuthingSuccess(response.getStatusCode(), response.getMessage(), "发送验证码失败");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送邮箱验证码失败, email={}", email, e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "发送验证码失败，请稍后重试");
        }
    }

    // ==================== 邮箱注册 ====================

    @Override
    public TokenResponse register(RegisterRequest request) {
        LoginUserContext existing = authRpcClient.getUserByUsername(request.getEmail());
        if (existing != null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "该邮箱已注册");
        }

        // 1. 先验证邀请码（不消费，仅校验有效性）
        inviteService.validateInviteCode(request.getInviteCode());

        try {
            // 2. 通过 Authing 验证邮箱验证码（仅校验验证码，不依赖注册结果）
            AuthenticationClient client = authingConfig.newAuthenticationClient();
            SignUpProfileDto profile = new SignUpProfileDto();
            profile.setPreferredUsername(request.getEmail());
            profile.setNickname(request.getEmail());

            UserSingleRespDto signUpResponse = client.signUpByEmailPassCode(
                    request.getEmail(),
                    request.getEmailCode(),
                    profile,
                    new SignUpOptionsDto()
            );

            // 验证码校验：200(新注册成功) 或"用户已存在"均视为验证码正确
            Integer statusCode = signUpResponse.getStatusCode();
            String responseMsg = signUpResponse.getMessage();
            boolean userAlreadyExists = statusCode != null && statusCode != 200
                    && StringUtils.isNotBlank(responseMsg) && responseMsg.contains("已存在");
            if (statusCode != null && statusCode != 200 && !userAlreadyExists) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR,
                        "验证码校验失败" + (StringUtils.isNotBlank(responseMsg) ? ": " + responseMsg : ""));
            }

            // 3. 在 auth-service 中注册用户
            String nickname = resolveEmailNickname(signUpResponse.getData(), request.getEmail());
            RegisterRpcResult result = authRpcClient.register(
                    request.getEmail(), request.getPassword(), request.getEmail(), null, nickname
            );
            if (!result.getSuccess()) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
            }

            UserRpcResponse registeredUser = result.hasUser() ? result.getUser() : null;
            if (registeredUser == null || registeredUser.getId() <= 0) {
                LoginUserContext loaded = authRpcClient.getUserByUsername(request.getEmail());
                if (loaded != null) {
                    registeredUser = UserRpcResponse.newBuilder()
                            .setId(loaded.getUserId())
                            .setUsername(loaded.getUsername())
                            .setEmail(loaded.getEmail())
                            .build();
                }
            }
            if (registeredUser == null || registeredUser.getId() <= 0) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册用户失败");
            }

            // 4. 邀请码已验证通过，正式消费
            Long inviterId = null;
            try {
                inviteService.useInviteCode(request.getInviteCode(), registeredUser.getId());
                var inviterRecord = inviteService.getInviter(registeredUser.getId());
                if (inviterRecord != null) {
                    inviterId = inviterRecord.getInviterId();
                }
            } catch (Exception e) {
                log.error("邮箱注册后邀请码消费失败，auth-service 已有孤儿用户 userId={}, email={}", registeredUser.getId(), request.getEmail(), e);
                throw e;
            }

            // 5. 创建本地用户信息
            userInfoService.createUserInfo(registeredUser.getId(), inviterId);

            // 6. 构建登录上下文
            LoginUserContext userContext = new LoginUserContext();
            userContext.setUserId(registeredUser.getId());
            userContext.setUsername(request.getEmail());
            userContext.setEmail(request.getEmail());
            userContext.setNickname(nickname);
            UserContext.setLoginUser(userContext);

            // 7. 返回 token
            return buildTokenResponse(result, registeredUser.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("邮箱注册失败, email={}", request.getEmail(), e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }
    }

    // ==================== 邮箱密码登录 ====================

    @Override
    public TokenResponse loginByEmail(String email, String password) {
        try {
            AuthResult authResult = authRpcClient.authenticate(email, password, null);
            if (!authResult.getSuccess()) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "邮箱或密码错误");
            }

            // 获取完整用户信息
            LoginUserContext userContext = authRpcClient.getUserById(authResult.getUserId());
            if (userContext == null) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "获取用户信息失败");
            }
            UserContext.setLoginUser(userContext);

            String accessToken = authRpcClient.generateToken(authResult.getUserId(), 1800);
            TokenResponse response = new TokenResponse();
            response.setAccessToken(accessToken);
            response.setTokenType("Bearer");
            response.setExpiresIn(1800L);
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("邮箱登录失败, email={}", email, e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "登录失败，请稍后重试");
        }
    }

    // ==================== 微信小程序扫码登录（三步） ====================

    @Override
    public WechatQrCodeResponse generateWechatQrCode() {
        try {
            AuthenticationClient client = authingConfig.newAuthenticationClient();
            GenerateQrcodeDto request = new GenerateQrcodeDto();
            request.setType(GenerateQrcodeDto.Type.WECHAT_MINIPROGRAM);

            GeneQRCodeRespDto response = client.geneQrCode(request);
            ensureAuthingSuccess(response.getStatusCode(), response.getMessage(), "生成二维码失败");

            WechatQrCodeResponse qrCodeResponse = new WechatQrCodeResponse();
            if (response.getData() != null) {
                qrCodeResponse.setQrcodeId(response.getData().getQrcodeId());
                qrCodeResponse.setQrCodeUrl(response.getData().getUrl());
            }
            qrCodeResponse.setStatus("PENDING");
            return qrCodeResponse;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成微信小程序登录二维码失败", e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "生成二维码失败");
        }
    }

    @Override
    public WechatQrCodeResponse queryWechatQrCodeStatus(String qrcodeId) {
        try {
            JsonNode response = doAuthingGet("/api/v3/check-qrcode-status?qrcodeId="
                    + URLEncoder.encode(qrcodeId, StandardCharsets.UTF_8));

            JsonNode data = response.get("data");
            if (data == null && response.has("status")) {
                data = response;
            }
            if (data == null) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "查询二维码状态失败");
            }

            WechatQrCodeResponse qrCodeResponse = new WechatQrCodeResponse();
            qrCodeResponse.setQrcodeId(qrcodeId);
            qrCodeResponse.setStatus(data.path("status").asText());
            qrCodeResponse.setTicket(data.path("ticket").asText(null));

            JsonNode briefUserInfo = data.get("briefUserInfo");
            if (briefUserInfo != null && !briefUserInfo.isMissingNode()) {
                qrCodeResponse.setDisplayName(briefUserInfo.path("displayName").asText(null));
                qrCodeResponse.setPhoto(briefUserInfo.path("photo").asText(null));
            }
            return qrCodeResponse;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询微信小程序登录二维码状态失败, qrcodeId={}", qrcodeId, e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "查询二维码状态失败");
        }
    }

    @Override
    public WechatLoginResponse loginByWechatQrCode(String ticket) {
        try {
            AuthenticationClient client = authingConfig.newAuthenticationClient();

            ExchangeTokenSetWithQRcodeTicketDto tokenRequest = new ExchangeTokenSetWithQRcodeTicketDto();
            tokenRequest.setTicket(ticket);
            tokenRequest.setClientId(authingConfig.getAppId());
            tokenRequest.setClientSecret(authingConfig.getAppSecret());

            LoginTokenRespDto tokenResponse = client.exchangeTokenSetWithQrCodeTicket(tokenRequest);
            ensureAuthingSuccess(tokenResponse.getStatusCode(), tokenResponse.getMessage(), "微信登录失败");

            if (tokenResponse.getData() == null || StringUtils.isBlank(tokenResponse.getData().getAccessToken())) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "微信登录失败");
            }

            UserInfo authingUserInfo = client.getUserInfoByAccessToken(tokenResponse.getData().getAccessToken());
            if (authingUserInfo == null || StringUtils.isBlank(authingUserInfo.getSub())) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "获取微信用户信息失败");
            }

            String localUsername = buildWechatUsername(authingUserInfo.getSub());
            LoginUserContext existingUser = authRpcClient.getUserByUsername(localUsername);

            WechatLoginResponse response = new WechatLoginResponse();

            if (existingUser != null) {
                // 老用户：直接登录
                UserContext.setLoginUser(existingUser);
                String token = authRpcClient.generateToken(existingUser.getUserId(), 1800);

                TokenResponse tokenResp = new TokenResponse();
                tokenResp.setAccessToken(token);
                tokenResp.setTokenType("Bearer");
                tokenResp.setExpiresIn(1800L);

                response.setNewUser(false);
                response.setToken(tokenResp);
            } else {
                // 新用户：暂存信息到 Redis，等待输入邀请码
                String tempToken = UUID.randomUUID().toString().replace("-", "");
                String tempKey = WECHAT_TEMP_KEY_PREFIX + tempToken;

                String nickname = resolveWechatNickname(authingUserInfo, localUsername);
                String picture = authingUserInfo.getPicture();

                String userData = localUsername + "|" + nickname + "|" + (picture != null ? picture : "");
                stringRedisTemplate.opsForValue().set(tempKey, userData, WECHAT_TEMP_EXPIRE_MINUTES, TimeUnit.MINUTES);

                response.setNewUser(true);
                response.setTempToken(tempToken);
                response.setNickname(nickname);
                response.setAvatar(picture);
            }

            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信小程序扫码登录失败", e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "微信登录失败");
        }
    }

    // ==================== 微信新用户完成注册 ====================

    @Override
    public TokenResponse completeWechatLogin(WechatCompleteRequest request) {
        String tempKey = WECHAT_TEMP_KEY_PREFIX + request.getTempToken();
        String userData = stringRedisTemplate.opsForValue().get(tempKey);

        if (userData == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "临时令牌已过期，请重新登录");
        }

        String[] parts = userData.split("\\|", -1);
        String username = parts[0];
        String nickname = parts[1];
        String avatar = parts.length > 2 ? parts[2] : "";

        try {
            // 1. 先验证邀请码（不消费，仅校验有效性）
            inviteService.validateInviteCode(request.getInviteCode());

            // 2. 验证通过后，在 auth-service 中注册用户
            String randomPassword = "WX_" + IdUtil.getSnowflakeNextIdStr();
            RegisterRpcResult result = authRpcClient.register(username, randomPassword, null, null, nickname);

            if (!result.getSuccess()) {
                LoginUserContext existing = authRpcClient.getUserByUsername(username);
                if (existing == null) {
                    throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册用户失败: " + result.getMessage());
                }
                // 用户已存在，直接登录
                UserContext.setLoginUser(existing);
                String token = authRpcClient.generateToken(existing.getUserId(), 1800);
                TokenResponse tokenResp = new TokenResponse();
                tokenResp.setAccessToken(token);
                tokenResp.setTokenType("Bearer");
                tokenResp.setExpiresIn(1800L);
                return tokenResp;
            }

            UserRpcResponse registeredUser = result.getUser();
            if (registeredUser == null || registeredUser.getId() <= 0) {
                throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册用户失败");
            }

            // 3. 邀请码已验证通过，正式消费
            try {
                inviteService.useInviteCode(request.getInviteCode(), registeredUser.getId());
            } catch (Exception e) {
                log.error("微信注册后邀请码消费失败，auth-service 已有孤儿用户 userId={}, username={}", registeredUser.getId(), username, e);
                throw e;
            }
            Long inviterId = null;
            var inviterRecord = inviteService.getInviter(registeredUser.getId());
            if (inviterRecord != null) {
                inviterId = inviterRecord.getInviterId();
            }

            // 4. 创建本地用户信息
            userInfoService.createUserInfo(registeredUser.getId(), inviterId);
            stringRedisTemplate.delete(tempKey);

            LoginUserContext userContext = new LoginUserContext();
            userContext.setUserId(registeredUser.getId());
            userContext.setUsername(username);
            userContext.setNickname(nickname);
            userContext.setAvatar(avatar.isEmpty() ? null : avatar);
            UserContext.setLoginUser(userContext);

            return buildTokenResponse(result, registeredUser.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信新用户完成注册失败, username={}", username, e);
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "注册失败，请稍后重试");
        }
    }

    // ==================== 登出 ====================

    @Override
    public void logout() {
        LoginUserContext user = UserContext.getLoginUser();
        if (user != null) {
            try {
                UserContext.removeLoginUser();
            } catch (Exception e) {
                log.warn("调用认证服务登出失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public LoginUserContext getLoginUser() {
        return UserContext.getLoginUser();
    }

    // ==================== 私有工具方法 ====================

    private TokenResponse buildTokenResponse(RegisterRpcResult result, Long userId) {
        TokenResponse response = new TokenResponse();
        TokenRpcResponse tokenData = result.hasToken() ? result.getToken() : null;
        if (tokenData != null) {
            response.setAccessToken(tokenData.getAccessToken());
            response.setRefreshToken(tokenData.getRefreshToken());
            response.setExpiresIn(tokenData.getExpiresIn());
        } else {
            response.setAccessToken(authRpcClient.generateToken(userId, 1800));
            response.setExpiresIn(1800L);
        }
        response.setTokenType("Bearer");
        return response;
    }

    private String buildWechatUsername(String authingSub) {
        return "wxmp_" + DigestUtils.md5Hex(authingSub);
    }

    private String resolveEmailNickname(UserDto user, String fallbackEmail) {
        if (user == null) {
            return fallbackEmail;
        }
        if (StringUtils.isNotBlank(user.getNickname())) {
            return user.getNickname();
        }
        if (StringUtils.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        if (StringUtils.isNotBlank(user.getEmail())) {
            return user.getEmail();
        }
        return fallbackEmail;
    }

    private String resolveWechatNickname(UserInfo userInfo, String fallback) {
        if (userInfo == null) {
            return fallback;
        }
        if (StringUtils.isNotBlank(userInfo.getNickname())) {
            return userInfo.getNickname();
        }
        if (StringUtils.isNotBlank(userInfo.getName())) {
            return userInfo.getName();
        }
        if (StringUtils.isNotBlank(userInfo.getPreferredUsername())) {
            return userInfo.getPreferredUsername();
        }
        return fallback;
    }

    private void ensureAuthingSuccess(Integer statusCode, String message, String errorPrefix) {
        if (statusCode == null || statusCode != 200) {
            throw new BusinessException(ResponseCode.SYSTEM_ERROR,
                    errorPrefix + (StringUtils.isNotBlank(message) ? ": " + message : ""));
        }
    }

    private JsonNode doAuthingGet(String pathWithQuery) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authingConfig.getAppHost() + pathWithQuery))
                .header("x-authing-request-from", "java-sdk")
                .header("x-authing-sdk-version", "1.0.0")
                .header("x-authing-app-id", authingConfig.getAppId())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Authing HTTP GET failed, path={}, status={}, body={}", pathWithQuery, response.statusCode(), response.body());
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "调用 Authing 服务失败");
        }

        return objectMapper.readTree(response.body());
    }
}
