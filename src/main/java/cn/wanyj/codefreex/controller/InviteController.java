package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.entity.Invite;
import cn.wanyj.codefreex.model.entity.InviteUser;
import cn.wanyj.codefreex.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author wanyj
 */
@Tag(name = "邀请码接口")
@RestController
@RequestMapping("/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @Operation(summary = "生成邀请码")
    @PostMapping("/generate")
    @AuthCheck
    public BaseResponse<Invite> generateInviteCode(
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) Integer expireHours,
            @RequestParam(defaultValue = "1") Integer maxUseCount) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(inviteService.generateInviteCode(userId, batch, expireHours, maxUseCount));
    }

    @Operation(summary = "使用邀请码")
    @PostMapping("/use")
    @AuthCheck
    public BaseResponse<Boolean> useInviteCode(@RequestParam String inviteCode) {
        Long userId = UserContext.getLoginUserId();
        inviteService.useInviteCode(inviteCode, userId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "查询我的邀请码列表")
    @GetMapping("/list")
    @AuthCheck
    public BaseResponse<PageResponse<Invite>> listInvites(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(inviteService.listInvites(userId, pageNum, pageSize));
    }

    @Operation(summary = "查询邀请人（谁邀请了我）")
    @GetMapping("/inviter")
    @AuthCheck
    public BaseResponse<InviteUser> getInviter() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(inviteService.getInviter(userId));
    }

    @Operation(summary = "查询邀请码使用记录")
    @GetMapping("/{inviteId}/users")
    @AuthCheck
    public BaseResponse<List<InviteUser>> listInviteUsers(@PathVariable Long inviteId) {
        return ResultUtils.success(inviteService.listInviteUsers(inviteId));
    }

    @Operation(summary = "分页查询所有邀请码（管理员）")
    @GetMapping("/admin/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<Invite>> listAllInvites(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return ResultUtils.success(inviteService.listAllInvites(pageNum, pageSize, status));
    }
}
