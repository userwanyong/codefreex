package cn.wanyj.codefreex.controller;

import cn.hutool.core.util.IdUtil;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.entity.CreditTransaction;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.OssService;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * @author wanyj
 */
@Tag(name = "用户接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserInfoService userInfoService;
    private final AuthRpcClient authRpcClient;
    private final CreditTransactionService creditTransactionService;
    private final OssService ossService;

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2MB

    @Operation(summary = "获取用户关联信息")
    @GetMapping("/info")
    @AuthCheck
    public BaseResponse<UserInfo> getUserInfo() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(userInfoService.getUserInfo(userId));
    }

    @Operation(summary = "获取用户角色")
    @GetMapping("/role")
    @AuthCheck
    public BaseResponse<java.util.List<String>> getUserRoles() {
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

    @Operation(summary = "上传头像")
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
        String ext = switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
        String objectKey = "avatar/" + userId + "/" + IdUtil.fastSimpleUUID() + "." + ext;

        try {
            String url = ossService.upload(objectKey, file.getBytes(), contentType);
            userInfoService.updateAvatar(userId, url);
            return ResultUtils.success(url);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "头像上传失败：" + e.getMessage());
        }
    }

    @Operation(summary = "更新个人资料")
    @PostMapping("/profile/update")
    @AuthCheck
    public BaseResponse<Boolean> updateProfile(@RequestParam(required = false) String nickname) {
        Long userId = UserContext.getLoginUserId();
        if (nickname != null && !nickname.isBlank()) {
            if (nickname.length() > 32) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "昵称长度不能超过32个字符");
            }
            userInfoService.updateNickname(userId, nickname.trim());
        }
        return ResultUtils.success(true);
    }
}
