package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.AppQueryRequest;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.FeaturedApplication;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.FeaturedApplicationService;
import cn.wanyj.codefreex.auth.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 应用管理接口（管理员侧）
 *
 * @author wanyj
 */
@Tag(name = "应用管理接口（管理员）")
@RestController
@RequestMapping("/app/admin")
@RequiredArgsConstructor
public class AppAdminController {

    private final AppService appService;
    private final FeaturedApplicationService featuredApplicationService;

    @Operation(summary = "管理员分页查询应用")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<App>> listAppsForAdmin(AppQueryRequest request) {
        return ResultUtils.success(appService.listAppsForAdmin(request));
    }

    @Operation(summary = "设置/取消精选应用")
    @PostMapping("/featured")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> setFeatured(
            @RequestParam Long appId,
            @RequestParam int featured) {
        appService.setFeatured(appId, featured);
        return ResultUtils.success(true);
    }

    @Operation(summary = "分页查询精选申请")
    @GetMapping("/featured-applications")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<FeaturedApplication>> listFeaturedApplications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(featuredApplicationService.listApplications(status, pageNum, pageSize));
    }

    @Operation(summary = "审批精选申请")
    @PostMapping("/featured-application/review")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> reviewFeaturedApplication(
            @RequestParam Long applicationId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String adminRemark) {
        Long reviewerId = UserContext.getLoginUserId();
        featuredApplicationService.reviewApplication(applicationId, reviewerId, approved, adminRemark);
        return ResultUtils.success(true);
    }

    @Operation(summary = "取消精选")
    @PostMapping("/featured-application/cancel")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> cancelFeatured(@RequestParam Long appId) {
        Long reviewerId = UserContext.getLoginUserId();
        featuredApplicationService.cancelFeatured(appId, reviewerId);
        return ResultUtils.success(true);
    }
}
