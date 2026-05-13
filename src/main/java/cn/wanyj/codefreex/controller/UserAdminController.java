package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.dto.request.UserQueryRequest;
import cn.wanyj.codefreex.model.dto.response.AdminUserVO;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
}
