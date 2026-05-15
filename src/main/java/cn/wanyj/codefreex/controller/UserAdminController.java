package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.CreditAdjustRequest;
import cn.wanyj.codefreex.model.dto.request.UserQueryRequest;
import cn.wanyj.codefreex.model.dto.response.AdminUserVO;
import cn.wanyj.codefreex.model.entity.CreditTransaction;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.NotificationService;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（管理员侧）
 *
 * @author wanyj
 */
@Tag(name = "用户管理接口（管理员）")
@RestController
@RequestMapping("/user/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserInfoService userInfoService;
    private final AuthRpcClient authRpcClient;
    private final CreditTransactionService creditTransactionService;
    private final NotificationService notificationService;

    @Operation(summary = "管理员分页查询用户")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<UserInfo>> listUsersForAdmin(UserQueryRequest request) {
        return ResultUtils.success(userInfoService.listUsersForAdmin(request));
    }

    @Operation(summary = "管理员获取用户详情")
    @GetMapping("/{userId}")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<AdminUserVO> getUserDetail(@PathVariable Long userId) {
        UserInfo localUser = userInfoService.getUserInfo(userId);
        if (localUser == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户不存在");
        }

        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(localUser.getUserId());
        vo.setNickname(localUser.getNickname());
        vo.setAvatar(localUser.getAvatar());
        vo.setStatus(localUser.getStatus());
        vo.setTotalCredits(localUser.getTotalCredits());
        vo.setRemainingCredits(localUser.getRemainingCredits());
        vo.setCreateTime(localUser.getCreateTime());

        // 从 RPC 获取邮箱、手机、角色
        try {
            LoginUserContext rpcUser = authRpcClient.getUserById(userId);
            if (rpcUser != null) {
                vo.setEmail(rpcUser.getEmail());
                vo.setPhone(rpcUser.getPhone());
                if (vo.getNickname() == null || vo.getNickname().isEmpty()) {
                    vo.setNickname(rpcUser.getNickname());
                }
                if (vo.getAvatar() == null || vo.getAvatar().isEmpty()) {
                    vo.setAvatar(rpcUser.getAvatar());
                }
            }
        } catch (Exception e) {
            // RPC 获取失败时使用本地数据
        }

        try {
            List<String> roles = authRpcClient.getUserRoles(userId);
            vo.setRoles(roles);
        } catch (Exception e) {
            // 角色获取失败不影响主流程
        }

        return ResultUtils.success(vo);
    }

    @Operation(summary = "管理员设置用户状态")
    @PostMapping("/status")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> setUserStatus(
            @RequestParam Long userId,
            @RequestParam String status) {
        userInfoService.setUserStatus(userId, status);
        return ResultUtils.success(true);
    }

    @Operation(summary = "管理员查询用户码点流水")
    @GetMapping("/{userId}/credit-transactions")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<CreditTransaction>> listCreditTransactions(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(creditTransactionService.listTransactions(userId, pageNum, pageSize));
    }

    @Operation(summary = "管理员分页查询所有码点流水")
    @GetMapping("/credit-transactions")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<CreditTransaction>> listAllCreditTransactions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type) {
        return ResultUtils.success(creditTransactionService.listAllTransactions(pageNum, pageSize, userId, type));
    }

    @Operation(summary = "管理员调整用户码点")
    @PostMapping("/adjust-credits")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> adjustCredits(@Valid @RequestBody CreditAdjustRequest request) {
        Long operatorId = UserContext.getLoginUserId();
        int amount = request.getAmount();

        if (amount > 0) {
            userInfoService.addCredits(request.getUserId(), amount);
        } else if (amount < 0) {
            userInfoService.deductCredits(request.getUserId(), -amount);
        }

        // 记录流水
        UserInfo updatedUserInfo = userInfoService.getUserInfo(request.getUserId());
        creditTransactionService.recordTransaction(
                request.getUserId(),
                amount > 0 ? CreditTransactionType.ADMIN_ADJUST : CreditTransactionType.ADMIN_ADJUST,
                amount,
                updatedUserInfo.getRemainingCredits(),
                CreditSourceType.ADMIN,
                null,
                request.getDescription() != null ? request.getDescription() : "管理员调整码点",
                operatorId
        );

        // 通知用户
        String desc = request.getDescription() != null ? request.getDescription() : "管理员调整码点";
        notificationService.createNotification(
                request.getUserId(),
                amount > 0 ? "码点充值通知" : "码点扣减通知",
                (amount > 0 ? "管理员为你充值了 " : "管理员扣减了你 ") + Math.abs(amount) + " 码点"
                        + "，当前余额：" + updatedUserInfo.getRemainingCredits()
                        + (desc != null && !desc.equals("管理员调整码点") ? "。原因：" + desc : ""),
                "credit_adjust",
                null
        );

        return ResultUtils.success(true);
    }
}
