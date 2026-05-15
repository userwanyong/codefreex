package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.entity.UserUsage;
import cn.wanyj.codefreex.service.UserUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量统计管理接口（管理员侧）
 *
 * @author wanyj
 */
@Tag(name = "用量统计管理接口（管理员）")
@RestController
@RequestMapping("/usage/admin")
@RequiredArgsConstructor
public class UserUsageAdminController {

    private final UserUsageService userUsageService;

    @Operation(summary = "管理员分页查询用量统计")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<UserUsage>> listUsages(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String status) {
        return ResultUtils.success(userUsageService.listUsagesForAdmin(pageNum, pageSize, userId, appId, modelId, status));
    }
}
