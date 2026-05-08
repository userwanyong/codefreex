package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.entity.Redeem;
import cn.wanyj.codefreex.model.entity.RedeemUser;
import cn.wanyj.codefreex.service.RedeemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author wanyj
 */
@Tag(name = "兑换码接口（管理员）")
@RestController
@RequestMapping("/redeem")
@RequiredArgsConstructor
public class RedeemController {

    private final RedeemService redeemService;

    @Operation(summary = "生成兑换码（管理员）")
    @PostMapping("/generate")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Redeem> generateRedeemCode(
            @RequestParam int quota,
            @RequestParam(required = false) String batch,
            @RequestParam(required = false) Integer expireHours,
            @RequestParam(defaultValue = "1") Integer maxUseCount) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(redeemService.generateRedeemCode(userId, quota, batch, expireHours, maxUseCount));
    }

    @Operation(summary = "使用兑换码")
    @PostMapping("/use")
    @AuthCheck
    public BaseResponse<Boolean> useRedeemCode(@RequestParam String redeemCode) {
        Long userId = UserContext.getLoginUserId();
        redeemService.useRedeemCode(redeemCode, userId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "分页查询兑换码列表（管理员）")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<Redeem>> listRedeems(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return ResultUtils.success(redeemService.listRedeems(pageNum, pageSize, status));
    }

    @Operation(summary = "查看兑换码详情")
    @GetMapping("/{redeemId}")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Redeem> getRedeemDetail(@PathVariable Long redeemId) {
        return ResultUtils.success(redeemService.getRedeemDetail(redeemId));
    }

    @Operation(summary = "查看兑换码使用记录")
    @GetMapping("/{redeemId}/users")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<RedeemUser>> listRedeemUsers(@PathVariable Long redeemId) {
        return ResultUtils.success(redeemService.listRedeemUsers(redeemId));
    }
}
