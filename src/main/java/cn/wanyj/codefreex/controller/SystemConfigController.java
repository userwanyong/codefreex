package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.SystemConfigUpdateRequest;
import cn.wanyj.codefreex.model.dto.response.CreditConfigVO;
import cn.wanyj.codefreex.model.dto.response.SystemConfigGroupVO;
import cn.wanyj.codefreex.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统配置接口（管理员）：AI 服务商密钥、码点计费等运行时配置
 *
 * @author wanyj
 */
@Tag(name = "系统配置接口（管理员）")
@RestController
@RequestMapping("/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "查询码点计费配置（价格信息，公开）")
    @GetMapping("/credit")
    public BaseResponse<CreditConfigVO> getCreditConfig() {
        return ResultUtils.success(systemConfigService.getCreditConfig());
    }

    @Operation(summary = "按分组查询全部配置")
    @GetMapping("/admin/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<SystemConfigGroupVO>> listConfigs() {
        return ResultUtils.success(systemConfigService.listGroupedConfigs());
    }

    @Operation(summary = "批量更新配置")
    @PostMapping("/admin/update")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> updateConfigs(@Valid @RequestBody SystemConfigUpdateRequest request) {
        systemConfigService.updateConfigs(request.getConfigs());
        return ResultUtils.success(true);
    }
}
