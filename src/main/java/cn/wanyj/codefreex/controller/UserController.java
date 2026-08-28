package cn.wanyj.codefreex.controller;

import cn.wanyj.auth.api.protobuf.OAuthBindingRpcResponse;
import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.BindContactRequest;
import cn.wanyj.codefreex.model.dto.request.ChangePasswordRequest;
import cn.wanyj.codefreex.model.dto.request.UserAdminUpdateRequest;
import cn.wanyj.codefreex.model.dto.response.AccountBindingsVO;
import cn.wanyj.codefreex.model.dto.response.OAuthBindingVO;
import cn.wanyj.codefreex.model.dto.response.UserProfileVO;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.CreditTransaction;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 个人中心接口：资料/头像/密码/账号绑定全部委托 auth-service RPC，
 * 码点与流水来自本地业务表。
 *
 * @author wanyj
 */
@Tag(name = "个人中心接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    /** 支持的 OAuth 提供方（与 auth-service LoginMethod 注册表保持一致） */
    private static final Set<String> SUPPORTED_OAUTH_PROVIDERS = Set.of("gitee", "github");

    private final UserInfoService userInfoService;
    private final AuthRpcClient authRpcClient;
    private final CreditTransactionService creditTransactionService;

    @Operation(summary = "获取个人中心信息（身份来自 auth-service，码点来自本地）")
    @GetMapping("/info")
    @AuthCheck
    public BaseResponse<UserProfileVO> getUserInfo() {
        Long userId = UserContext.getLoginUserId();
        UserRpcResponse rpcUser = authRpcClient.getUserRpcById(userId);
        if (rpcUser == null || rpcUser.getId() == 0) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR);
        }
        UserInfo profile = userInfoService.getUserInfo(userId);

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(rpcUser.getId());
        vo.setUsername(rpcUser.getUsername());
        vo.setNickname(rpcUser.getNickname());
        vo.setAvatar(rpcUser.getAvatar());
        vo.setEmail(rpcUser.getEmail());
        vo.setEmailVerified(rpcUser.getEmailVerified());
        vo.setPhone(rpcUser.getPhone());
        vo.setPhoneVerified(rpcUser.getPhoneVerified());
        vo.setRoles(new ArrayList<>(rpcUser.getRolesList()));
        vo.setCreateTime(toLocalDateTime(rpcUser.getCreatedAt()));
        vo.setUpdateTime(toLocalDateTime(rpcUser.getUpdatedAt()));
        if (profile != null) {
            vo.setInviterId(profile.getInviterId());
            vo.setTotalCredits(profile.getTotalCredits());
            vo.setRemainingCredits(profile.getRemainingCredits());
        }
        return ResultUtils.success(vo);
    }

    @Operation(summary = "获取用户角色")
    @GetMapping("/role")
    @AuthCheck
    public BaseResponse<List<String>> getUserRoles() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(authRpcClient.getUserRoles(userId));
    }

    @Operation(summary = "查询我的码点流水")
    @GetMapping("/credit-transactions")
    @AuthCheck
    public BaseResponse<PageResponse<CreditTransaction>> listMyCreditTransactions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(creditTransactionService.listTransactions(userId, pageNum, pageSize));
    }

    @Operation(summary = "更新个人资料（昵称等，写入 auth-service）")
    @PostMapping("/profile/update")
    @AuthCheck
    public BaseResponse<Boolean> updateProfile(@RequestBody UserAdminUpdateRequest request) {
        Long userId = UserContext.getLoginUserId();
        if (request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            if (nickname.isEmpty()) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "昵称不能为空");
            }
            if (nickname.length() > 32) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "昵称长度不能超过32个字符");
            }
            UserAdminUpdateRequest update = new UserAdminUpdateRequest();
            update.setUserId(userId);
            update.setNickname(nickname);
            OperationResult result = authRpcClient.updateUser(update);
            if (!result.getSuccess()) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
            }
            // 同步当前请求上下文，避免本次响应中昵称滞后
            LoginUserContext ctx = UserContext.getLoginUser();
            if (ctx != null) {
                ctx.setNickname(nickname);
                UserContext.setLoginUser(ctx);
            }
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "上传头像（经 auth-service 对象存储）")
    @PostMapping("/avatar/upload")
    @AuthCheck
    public BaseResponse<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "文件不能为空");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "头像文件不能超过 2MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "仅支持 JPG、PNG、GIF、WebP 格式");
        }

        Long userId = UserContext.getLoginUserId();
        try {
            String url = authRpcClient.uploadAvatar(userId, file.getOriginalFilename(), contentType, file.getBytes());
            if (StringUtils.isBlank(url)) {
                throw new BusinessException(ResponseCode.OPERATION_ERROR, "头像上传失败");
            }

            // 头像地址写回 auth-service 用户资料
            UserAdminUpdateRequest update = new UserAdminUpdateRequest();
            update.setUserId(userId);
            update.setAvatar(url);
            OperationResult result = authRpcClient.updateUser(update);
            if (!result.getSuccess()) {
                throw new BusinessException(ResponseCode.OPERATION_ERROR, "头像更新失败：" + result.getMessage());
            }

            LoginUserContext ctx = UserContext.getLoginUser();
            if (ctx != null) {
                ctx.setAvatar(url);
                UserContext.setLoginUser(ctx);
            }
            return ResultUtils.success(url);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "头像上传失败：" + e.getMessage());
        }
    }

    @Operation(summary = "修改密码（校验旧密码，auth-service 执行）")
    @PostMapping("/password/change")
    @AuthCheck
    public BaseResponse<Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getLoginUserId();
        OperationResult result = authRpcClient.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    // ==================== 账号绑定 ====================

    @Operation(summary = "查询账号绑定信息（邮箱/手机/第三方）")
    @GetMapping("/bindings")
    @AuthCheck
    public BaseResponse<AccountBindingsVO> getBindings() {
        Long userId = UserContext.getLoginUserId();
        UserRpcResponse rpcUser = authRpcClient.getUserRpcById(userId);
        if (rpcUser == null || rpcUser.getId() == 0) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR);
        }

        AccountBindingsVO vo = new AccountBindingsVO();
        vo.setEmail(rpcUser.getEmail());
        vo.setEmailVerified(rpcUser.getEmailVerified());
        vo.setPhone(rpcUser.getPhone());
        vo.setPhoneVerified(rpcUser.getPhoneVerified());

        String emailMethod = resolveEmailBindMethod();
        vo.setEmailBindable(emailMethod != null);
        vo.setEmailBindMethod(emailMethod);
        vo.setPhoneBindable(authRpcClient.listEnabledLoginMethods().contains("sms:aliyun"));

        List<OAuthBindingVO> bindings = new ArrayList<>();
        for (OAuthBindingRpcResponse binding : authRpcClient.listOAuthBindings(userId)) {
            OAuthBindingVO bindingVO = new OAuthBindingVO();
            bindingVO.setProvider(binding.getProvider());
            bindingVO.setProviderUid(binding.getProviderUid());
            bindingVO.setCreateTime(toLocalDateTime(binding.getCreatedAt()));
            bindings.add(bindingVO);
        }
        vo.setOauthBindings(bindings);
        return ResultUtils.success(vo);
    }

    @Operation(summary = "发送绑定邮箱的验证码")
    @PostMapping("/bindings/email/send-code")
    @AuthCheck
    public BaseResponse<Boolean> sendEmailBindCode(@RequestBody BindContactRequest request) {
        if (StringUtils.isBlank(request.getTarget())) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邮箱地址不能为空");
        }
        String method = resolveEmailBindMethod();
        if (method == null) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "邮箱验证码服务未启用，请联系管理员");
        }
        OperationResult result = authRpcClient.sendCode(method, request.getTarget().trim());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "绑定/换绑邮箱（验证码校验通过后覆盖旧值）")
    @PostMapping("/bindings/email")
    @AuthCheck
    public BaseResponse<Boolean> bindEmail(@Valid @RequestBody BindContactRequest request) {
        Long userId = UserContext.getLoginUserId();
        String method = resolveEmailBindMethod();
        if (method == null) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "邮箱验证码服务未启用，请联系管理员");
        }
        OperationResult result = authRpcClient.bindEmail(userId, method, request.getTarget().trim(), request.getCode());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "解绑邮箱")
    @PostMapping("/bindings/email/unbind")
    @AuthCheck
    public BaseResponse<Boolean> unbindEmail() {
        Long userId = UserContext.getLoginUserId();
        OperationResult result = authRpcClient.unbindEmail(userId);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "发送绑定手机号的验证码")
    @PostMapping("/bindings/phone/send-code")
    @AuthCheck
    public BaseResponse<Boolean> sendPhoneBindCode(@RequestBody BindContactRequest request) {
        if (StringUtils.isBlank(request.getTarget())) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "手机号不能为空");
        }
        if (!authRpcClient.listEnabledLoginMethods().contains("sms:aliyun")) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "短信验证码服务未启用，请联系管理员");
        }
        OperationResult result = authRpcClient.sendCode("sms:aliyun", request.getTarget().trim());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "绑定/换绑手机号（验证码校验通过后覆盖旧值）")
    @PostMapping("/bindings/phone")
    @AuthCheck
    public BaseResponse<Boolean> bindPhone(@Valid @RequestBody BindContactRequest request) {
        Long userId = UserContext.getLoginUserId();
        if (!authRpcClient.listEnabledLoginMethods().contains("sms:aliyun")) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "短信验证码服务未启用，请联系管理员");
        }
        OperationResult result = authRpcClient.bindPhone(userId, "sms:aliyun", request.getTarget().trim(), request.getCode());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "解绑手机号")
    @PostMapping("/bindings/phone/unbind")
    @AuthCheck
    public BaseResponse<Boolean> unbindPhone() {
        Long userId = UserContext.getLoginUserId();
        OperationResult result = authRpcClient.unbindPhone(userId);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "获取第三方账号绑定授权页地址（前端跳转发起绑定）")
    @GetMapping("/bindings/oauth/{provider}/authorize")
    @AuthCheck
    public BaseResponse<String> oauthBindAuthorize(@PathVariable String provider) {
        Long userId = UserContext.getLoginUserId();
        String normalized = normalizeProvider(provider);
        String url = authRpcClient.buildOAuthBindUrl(userId, normalized);
        if (StringUtils.isBlank(url)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    normalized + " 未启用、未配置凭证或你已绑定该平台");
        }
        return ResultUtils.success(url);
    }

    @Operation(summary = "解绑第三方平台")
    @PostMapping("/bindings/oauth/{provider}/unbind")
    @AuthCheck
    public BaseResponse<Boolean> unbindOAuth(@PathVariable String provider) {
        Long userId = UserContext.getLoginUserId();
        OperationResult result = authRpcClient.unbindOAuth(userId, normalizeProvider(provider));
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    // ==================== 私有工具方法 ====================

    /**
     * 解析当前可用的邮箱验证码方式（绑定邮箱复用登录验证码通道）
     */
    private String resolveEmailBindMethod() {
        List<String> methods = authRpcClient.listEnabledLoginMethods();
        if (methods.contains("email:smtp")) {
            return "email:smtp";
        }
        if (methods.contains("email:aliyun")) {
            return "email:aliyun";
        }
        return null;
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

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return epochMillis > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()) : null;
    }
}
