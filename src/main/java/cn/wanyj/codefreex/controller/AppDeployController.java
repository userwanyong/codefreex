package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.response.AppDeployResponse;
import cn.wanyj.codefreex.service.AppDeployService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 应用部署控制器
 *
 * @author BanXia
 */
@Tag(name = "应用部署接口")
@RestController
@RequestMapping("/app/deploy")
@RequiredArgsConstructor
public class AppDeployController {

    private final AppDeployService appDeployService;

    @Operation(summary = "部署应用")
    @PostMapping
    @AuthCheck
    public BaseResponse<AppDeployResponse> deploy(@RequestParam Long appId) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(appDeployService.deployApp(userId, appId));
    }

    @Operation(summary = "取消部署")
    @PostMapping("/cancel")
    @AuthCheck
    public BaseResponse<Boolean> cancelDeploy(@RequestParam Long appId) {
        Long userId = UserContext.getLoginUserId();
        appDeployService.cancelDeploy(userId, appId);
        return ResultUtils.success(true);
    }
}
