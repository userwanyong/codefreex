package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.EmailCodeSendRequest;
import cn.wanyj.codefreex.model.dto.request.RegisterRequest;
import cn.wanyj.codefreex.model.dto.request.WechatCompleteRequest;
import cn.wanyj.codefreex.model.dto.request.WechatQrCodeLoginRequest;
import cn.wanyj.codefreex.model.dto.request.WechatQrCodeStatusRequest;
import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import cn.wanyj.codefreex.model.dto.response.WechatLoginResponse;
import cn.wanyj.codefreex.model.dto.response.WechatQrCodeResponse;
import cn.wanyj.codefreex.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author wanyj
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送邮箱注册验证码")
    @PostMapping("/email/code")
    public BaseResponse<Boolean> sendEmailCode(@Valid @RequestBody EmailCodeSendRequest request) {
        authService.sendEmailCode(request.getEmail());
        return ResultUtils.success(true);
    }

    @Operation(summary = "邮箱注册（验证码 + 密码 + 邀请码）")
    @PostMapping("/register")
    public BaseResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResultUtils.success(authService.register(request));
    }

    @Operation(summary = "邮箱密码登录")
    @PostMapping("/login/email")
    public BaseResponse<TokenResponse> loginByEmail(
            @RequestParam String email,
            @RequestParam String password) {
        return ResultUtils.success(authService.loginByEmail(email, password));
    }

    @Operation(summary = "生成微信小程序登录二维码")
    @PostMapping("/wechat/qrcode/generate")
    public BaseResponse<WechatQrCodeResponse> generateWechatQrCode() {
        return ResultUtils.success(authService.generateWechatQrCode());
    }

    @Operation(summary = "查询微信小程序登录二维码状态")
    @PostMapping("/wechat/qrcode/status")
    public BaseResponse<WechatQrCodeResponse> queryWechatQrCodeStatus(
            @Valid @RequestBody WechatQrCodeStatusRequest request) {
        return ResultUtils.success(authService.queryWechatQrCodeStatus(request.getQrcodeId()));
    }

    @Operation(summary = "微信小程序扫码登录（用 ticket 换取用户信息）")
    @PostMapping("/wechat/qrcode/login")
    public BaseResponse<WechatLoginResponse> loginByWechatQrCode(
            @Valid @RequestBody WechatQrCodeLoginRequest request) {
        return ResultUtils.success(authService.loginByWechatQrCode(request.getTicket()));
    }

    @Operation(summary = "微信新用户完成注册（提交邀请码）")
    @PostMapping("/wechat/complete")
    public BaseResponse<TokenResponse> completeWechatLogin(@Valid @RequestBody WechatCompleteRequest request) {
        return ResultUtils.success(authService.completeWechatLogin(request));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    @AuthCheck
    public BaseResponse<Boolean> logout() {
        authService.logout();
        return ResultUtils.success(true);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/user/info")
    @AuthCheck
    public BaseResponse<LoginUserContext> getLoginUser() {
        return ResultUtils.success(authService.getLoginUser());
    }
}
