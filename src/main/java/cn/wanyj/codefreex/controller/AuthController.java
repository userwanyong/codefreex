package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.OAuthLoginOutcome;
import cn.wanyj.codefreex.model.dto.request.LoginCodeRequest;
import cn.wanyj.codefreex.model.dto.request.LoginPasswordRequest;
import cn.wanyj.codefreex.model.dto.request.OAuthCompleteRequest;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.request.SendCodeRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import cn.wanyj.codefreex.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 认证接口：全部认证授权能力委托 auth-service 微服务（RPC），
 * 登录方式（密码/验证码/OAuth）由管理端配置动态开闭。
 *
 * @author wanyj
 */
@Slf4j
@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";

    private final AuthService authService;
    private final AuthRpcClient authRpcClient;

    @Operation(summary = "查询当前开放的登录方式（管理端开闭实时同步）")
    @GetMapping("/login/methods")
    public BaseResponse<List<String>> listLoginMethods() {
        return ResultUtils.success(authService.listLoginMethods());
    }

    @Operation(summary = "账号密码登录（用户名或邮箱）")
    @PostMapping("/login/password")
    public BaseResponse<TokenResponse> loginByPassword(@Valid @RequestBody LoginPasswordRequest request) {
        return ResultUtils.success(authService.loginByPassword(request.getUsername(), request.getPassword()));
    }

    @Operation(summary = "发送登录验证码（邮箱/短信）")
    @PostMapping("/send-code")
    public BaseResponse<Boolean> sendCode(@Valid @RequestBody SendCodeRequest request) {
        authService.sendCode(request.getMethod(), request.getTarget());
        return ResultUtils.success(true);
    }

    @Operation(summary = "验证码登录（新用户自动注册，需邀请码）")
    @PostMapping("/login/code")
    public BaseResponse<TokenResponse> loginByCode(@Valid @RequestBody LoginCodeRequest request) {
        return ResultUtils.success(authService.loginByCode(request));
    }

    @Operation(summary = "邮箱注册（密码 + 邀请码）")
    @PostMapping("/register")
    public BaseResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResultUtils.success(authService.register(request));
    }

    @Operation(summary = "获取 OAuth 授权页地址（前端跳转发起授权）")
    @GetMapping("/oauth/{provider}/authorize")
    public BaseResponse<String> oauthAuthorize(@PathVariable String provider) {
        return ResultUtils.success(authService.buildOAuthAuthorizeUrl(provider));
    }

    @Operation(summary = "OAuth 提供方回调（重定向回前端登录页）")
    @GetMapping("/oauth/{provider}/callback")
    public void oauthCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        OAuthLoginOutcome outcome;
        try {
            outcome = authService.handleOAuthCallback(provider, code, state);
        } catch (Exception e) {
            log.error("OAuth 回调处理失败, provider={}", provider, e);
            outcome = OAuthLoginOutcome.ofFailed("OAuth 登录失败，请稍后重试");
        }

        // 绑定流程回调：跳回个人中心并携带绑定结果
        if (outcome.isBind()) {
            String bindUrl = new StringBuilder(authRpcClient.getFrontendUrl())
                    .append("/profile#bind=")
                    .append(outcome.isBindSuccess() ? "success" : "failed")
                    .append("&message=").append(encode(outcome.getMessage()))
                    .toString();
            response.sendRedirect(response.encodeRedirectURL(bindUrl));
            return;
        }

        StringBuilder url = new StringBuilder(authRpcClient.getFrontendUrl())
                .append("/login#oauth=");
        if (outcome.isPending()) {
            url.append("pending")
                    .append("&tempToken=").append(encode(outcome.getTempToken()))
                    .append("&nickname=").append(encode(outcome.getNickname()));
        } else if (outcome.getToken() != null && outcome.getToken().getAccessToken() != null) {
            url.append("success")
                    .append("&accessToken=").append(encode(outcome.getToken().getAccessToken()))
                    .append("&refreshToken=").append(encode(outcome.getToken().getRefreshToken()));
        } else {
            url.append("failed")
                    .append("&message=").append(encode(outcome.getMessage()));
        }
        response.sendRedirect(response.encodeRedirectURL(url.toString()));
    }

    @Operation(summary = "OAuth 新用户完成注册（提交邀请码）")
    @PostMapping("/oauth/complete")
    public BaseResponse<TokenResponse> completeOAuthRegistration(@Valid @RequestBody OAuthCompleteRequest request) {
        return ResultUtils.success(authService.completeOAuthRegistration(request));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public BaseResponse<TokenResponse> refresh(@RequestHeader(REFRESH_TOKEN_HEADER) String refreshToken) {
        return ResultUtils.success(authService.refresh(refreshToken));
    }

    @Operation(summary = "用户登出（拉黑 access + 删除 refresh）")
    @PostMapping("/logout")
    @AuthCheck
    public BaseResponse<Boolean> logout(
            HttpServletRequest request,
            @RequestHeader(value = REFRESH_TOKEN_HEADER, required = false) String refreshToken) {
        authService.logout(extractAccessToken(request), refreshToken);
        return ResultUtils.success(true);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/user/info")
    @AuthCheck
    public BaseResponse<LoginUserContext> getLoginUser() {
        return ResultUtils.success(authService.getLoginUser());
    }

    private String extractAccessToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
