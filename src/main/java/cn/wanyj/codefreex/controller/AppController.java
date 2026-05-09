package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.AppCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AppEditRequest;
import cn.wanyj.codefreex.model.dto.response.AppVO;
import cn.wanyj.codefreex.model.dto.response.FeaturedAppResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.service.AppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "创建应用")
    @PostMapping("/create")
    @AuthCheck
    public BaseResponse<App> createApp(@Valid @RequestBody AppCreateRequest request) {
        Long userId = UserContext.getLoginUserId();
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
            @RequestParam(defaultValue = "10") int size) {
        return ResultUtils.success(appService.listFeaturedApps(cursor, size));
    }
}
