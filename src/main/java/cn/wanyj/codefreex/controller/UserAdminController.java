package cn.wanyj.codefreex.controller;

import cn.wanyj.auth.api.protobuf.OAuthBindingRpcResponse;
import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.RegisterRpcResult;
import cn.wanyj.auth.api.protobuf.UserPageResponse;
import cn.wanyj.auth.api.protobuf.UserRpcResponse;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.request.CreditAdjustRequest;
import cn.wanyj.codefreex.model.dto.request.UserAdminCreateRequest;
import cn.wanyj.codefreex.model.dto.request.UserAdminUpdateRequest;
import cn.wanyj.codefreex.model.dto.request.UserQueryRequest;
import cn.wanyj.codefreex.model.dto.request.UserRoleAssignRequest;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理接口（管理员侧）：用户身份/角色/状态统一走 auth-service RPC，码点留本地业务表
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

    /** 状态过滤时 RPC 不支持服务端筛选，超量拉取后在本地过滤 */
    private static final int STATUS_FILTER_FETCH_SIZE = 200;

    @Operation(summary = "管理员分页查询用户（数据来源 auth-service）")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<AdminUserVO>> listUsersForAdmin(UserQueryRequest request) {
        int pageNum = Math.max(request.getPageNum(), 1);
        int pageSize = Math.min(Math.max(request.getPageSize(), 1), 50);

        boolean filterStatus = request.getStatus() != null;
        int fetchSize = filterStatus ? STATUS_FILTER_FETCH_SIZE : pageSize;
        UserPageResponse page = authRpcClient.searchUsers(
                StringUtils.defaultString(request.getSearchKey()), pageNum, fetchSize);

        List<UserRpcResponse> items = new ArrayList<>(page.getItemsList());
        if (filterStatus) {
            items = items.stream()
                    .filter(user -> user.getStatus() == request.getStatus())
                    .toList();
        }

        Set<Long> userIds = items.stream().map(UserRpcResponse::getId).collect(Collectors.toSet());
        Map<Long, UserInfo> profileMap = userInfoService.batchGetUserInfos(userIds);

        List<AdminUserVO> voList = items.stream()
                .limit(pageSize)
                .map(user -> toAdminUserVO(user, profileMap.get(user.getId()), null))
                .toList();

        // 状态过滤在本地完成，总数以过滤后为准（单次最多拉取 200 条）
        long total = filterStatus ? items.size() : page.getTotal();
        return ResultUtils.success(PageResponse.of(voList, total, pageNum, pageSize));
    }

    @Operation(summary = "管理员获取用户详情（含第三方绑定）")
    @GetMapping("/{userId}")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<AdminUserVO> getUserDetail(@PathVariable Long userId) {
        UserRpcResponse rpcUser = authRpcClient.findUserForAdmin(userId);
        if (rpcUser == null || rpcUser.getId() == 0) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户不存在");
        }

        List<String> oauthProviders = authRpcClient.listOAuthBindings(userId).stream()
                .map(OAuthBindingRpcResponse::getProvider)
                .toList();
        UserInfo profile = userInfoService.getUserInfo(userId);

        AdminUserVO vo = toAdminUserVO(rpcUser, profile, oauthProviders);
        return ResultUtils.success(vo);
    }

    @Operation(summary = "管理员创建用户（auth-service 注册，默认 ROLE_USER）")
    @PostMapping("/create")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Long> createUser(@Valid @RequestBody UserAdminCreateRequest request) {
        if (authRpcClient.getUserByUsername(request.getUsername()) != null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "账号已存在");
        }
        RegisterRpcResult result = authRpcClient.register(
                request.getUsername(), request.getPassword(), request.getEmail(), request.getNickname());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    StringUtils.isNotBlank(result.getMessage()) ? result.getMessage() : "创建用户失败");
        }
        long userId = result.getUser().getId();

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            OperationResult assignResult = authRpcClient.assignRoles(userId, request.getRoleIds());
            if (!assignResult.getSuccess()) {
                throw new BusinessException(ResponseCode.OPERATION_ERROR, assignResult.getMessage());
            }
        }

        // 创建本地业务档案（无邀请人，初始码点为 0）
        userInfoService.createUserInfo(userId, null);
        return ResultUtils.success(userId);
    }

    @Operation(summary = "管理员编辑用户（null 字段不更新）")
    @PostMapping("/update")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> updateUser(@Valid @RequestBody UserAdminUpdateRequest request) {
        OperationResult result = authRpcClient.updateUser(request);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "管理员为用户分配角色（全量替换）")
    @PostMapping("/roles")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> assignRoles(@Valid @RequestBody UserRoleAssignRequest request) {
        OperationResult result = authRpcClient.assignRoles(request.getUserId(), request.getRoleIds());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "管理员设置用户状态（1-正常，0-禁用，禁用后立即踢下线）")
    @PostMapping("/status")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> setUserStatus(@RequestParam Long userId, @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "状态只能为 0（禁用）或 1（正常）");
        }
        OperationResult result = authRpcClient.updateUserStatus(userId, status);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        if (status == 0) {
            authRpcClient.revokeAllTokens(userId);
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "管理员删除用户（auth-service 物理删除 + 清理本地业务档案）")
    @PostMapping("/{userId}/delete")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> deleteUser(@PathVariable Long userId) {
        OperationResult result = authRpcClient.deleteUser(userId);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        authRpcClient.revokeAllTokens(userId);
        userInfoService.deleteUserInfo(userId);
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
        int balanceAfter;

        if (amount > 0) {
            balanceAfter = userInfoService.addCredits(request.getUserId(), amount);
        } else if (amount < 0) {
            balanceAfter = userInfoService.deductCredits(request.getUserId(), -amount);
        } else {
            UserInfo userInfo = userInfoService.getUserInfo(request.getUserId());
            if (userInfo == null) {
                throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户不存在");
            }
            balanceAfter = userInfo.getRemainingCredits();
        }

        // 记录流水
        creditTransactionService.recordTransaction(
                request.getUserId(),
                CreditTransactionType.ADMIN_ADJUST,
                amount,
                balanceAfter,
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
                        + "，当前余额：" + balanceAfter
                        + (desc != null && !desc.equals("管理员调整码点") ? "。原因：" + desc : ""),
                "credit_adjust",
                null
        );

        return ResultUtils.success(true);
    }

    /**
     * RPC 用户 + 本地业务档案 → 管理端视图
     */
    private AdminUserVO toAdminUserVO(UserRpcResponse user, UserInfo profile, List<String> oauthProviders) {
        AdminUserVO vo = new AdminUserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setRoles(new ArrayList<>(user.getRolesList()));
        vo.setCreateTime(toLocalDateTime(user.getCreatedAt()));
        vo.setOauthProviders(oauthProviders);
        if (profile != null) {
            vo.setTotalCredits(profile.getTotalCredits());
            vo.setRemainingCredits(profile.getRemainingCredits());
        }
        return vo;
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return epochMillis > 0 ? LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()) : null;
    }
}
