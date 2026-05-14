package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.request.AppCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AppEditRequest;
import cn.wanyj.codefreex.model.dto.response.AppVO;
import cn.wanyj.codefreex.model.dto.response.FeaturedAppResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.model.entity.FeaturedApplication;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.FeaturedApplicationService;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用管理接口（用户侧）
 *
 * @author wanyj
 */
@Tag(name = "应用管理接口")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final UserInfoService userInfoService;
    private final CreditTransactionService creditTransactionService;
    private final FeaturedApplicationService featuredApplicationService;

    @Operation(summary = "创建应用")
    @PostMapping("/create")
    @AuthCheck
    public BaseResponse<App> createApp(@Valid @RequestBody AppCreateRequest request) {
        Long userId = UserContext.getLoginUserId();

        // 检查码点余额（首次生成需要 FIRST_GENERATE_COST 码点）
        UserInfo userInfo = userInfoService.getUserInfo(userId);
        if (userInfo == null || userInfo.getRemainingCredits() < AppConstant.FIRST_GENERATE_COST) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR,
                    "码点不足，需要 " + AppConstant.FIRST_GENERATE_COST + " 码点才能创建应用，请先兑换码点");
        }

        // 扣减码点
        userInfoService.deductCredits(userId, AppConstant.FIRST_GENERATE_COST);

        // 记录码点流水
        UserInfo updatedUserInfo = userInfoService.getUserInfo(userId);
        creditTransactionService.recordTransaction(
                userId,
                CreditTransactionType.CONSUME,
                -AppConstant.FIRST_GENERATE_COST,
                updatedUserInfo.getRemainingCredits(),
                CreditSourceType.AI_CHAT,
                null,
                "创建应用（首次生成）",
                null
        );

        return ResultUtils.success(appService.createApp(userId, request));
    }

    @Operation(summary = "编辑应用")
    @PostMapping("/edit")
    @AuthCheck
    public BaseResponse<Boolean> editApp(@Valid @RequestBody AppEditRequest request) {
        Long userId = UserContext.getLoginUserId();
        appService.editApp(userId, request);
        return ResultUtils.success(true);
    }

    @Operation(summary = "查看应用详情")
    @GetMapping("/{appId}")
    public BaseResponse<AppVO> getAppById(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(appService.getAppById(appId, userId));
    }

    @Operation(summary = "删除应用")
    @PostMapping("/delete")
    @AuthCheck
    public BaseResponse<Boolean> deleteApp(@RequestParam Long appId) {
        Long userId = UserContext.getLoginUserId();
        appService.deleteApp(userId, appId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "分页查询我的应用")
    @GetMapping("/my/list")
    @AuthCheck
    public BaseResponse<PageResponse<AppVO>> listMyApps(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(appService.listMyApps(userId, pageNum, pageSize));
    }

    @Operation(summary = "游标分页查询精选应用")
    @GetMapping("/featured/list")
    public BaseResponse<FeaturedAppResponse> listFeaturedApps(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tag) {
        return ResultUtils.success(appService.listFeaturedApps(cursor, size, tag));
    }

    @Operation(summary = "获取精选应用的所有标签")
    @GetMapping("/featured/tags")
    public BaseResponse<List<String>> listFeaturedTags() {
        return ResultUtils.success(appService.listFeaturedTags());
    }

    @Operation(summary = "切换点赞")
    @PostMapping("/{appId}/like")
    @AuthCheck
    public BaseResponse<Boolean> likeApp(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        boolean liked = appService.likeApp(appId, userId);
        return ResultUtils.success(liked);
    }

    @Operation(summary = "查询点赞状态")
    @GetMapping("/{appId}/like/status")
    @AuthCheck
    public BaseResponse<Boolean> getLikeStatus(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(appService.isLiked(appId, userId));
    }

    @Operation(summary = "申请精选")
    @PostMapping("/{appId}/apply-featured")
    @AuthCheck
    public BaseResponse<FeaturedApplication> applyFeatured(
            @PathVariable Long appId,
            @RequestParam(required = false) String reason) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(featuredApplicationService.applyFeatured(appId, userId, reason));
    }

    @Operation(summary = "查询精选申请状态")
    @GetMapping("/{appId}/featured-application")
    @AuthCheck
    public BaseResponse<FeaturedApplication> getFeaturedApplication(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(featuredApplicationService.getLatestApplication(appId, userId));
    }
}
