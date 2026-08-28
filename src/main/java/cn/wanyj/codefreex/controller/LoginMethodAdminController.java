package cn.wanyj.codefreex.controller;

import cn.wanyj.auth.api.protobuf.LoginMethodRpcResponse;
import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.request.LoginMethodSaveRequest;
import cn.wanyj.codefreex.model.dto.response.LoginMethodVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录方式配置接口（租户级，管理员侧）：与 auth-service 控制台能力一致，
 * 开闭实时影响登录页展示（/auth/login/methods）。
 *
 * @author wanyj
 */
@Tag(name = "登录方式配置接口（管理员）")
@RestController
@RequestMapping("/auth/admin/login-methods")
@RequiredArgsConstructor
public class LoginMethodAdminController {

    private final AuthRpcClient authRpcClient;

    @Operation(summary = "查询本租户可配置的登录方式")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<LoginMethodVO>> listLoginMethods() {
        List<LoginMethodVO> result = new ArrayList<>();
        for (LoginMethodRpcResponse method : authRpcClient.listTenantLoginMethods()) {
            LoginMethodVO vo = new LoginMethodVO();
            vo.setMethod(method.getMethod());
            vo.setCategory(method.getCategory());
            vo.setDisplayName(method.getDisplayName());
            vo.setEnabled(method.getEnabled());
            vo.setUsePlatformConfig(method.getUsePlatformConfig());
            vo.setHasConfig(method.getHasConfig());
            vo.setPlatformEnabled(method.getPlatformEnabled());
            result.add(vo);
        }
        return ResultUtils.success(result);
    }

    @Operation(summary = "保存登录方式开关与凭证来源")
    @PostMapping("/save")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> saveLoginMethod(@Valid @RequestBody LoginMethodSaveRequest request) {
        int usePlatformConfig = request.getUsePlatformConfig() == null ? 1 : request.getUsePlatformConfig();
        OperationResult result = authRpcClient.saveTenantLoginMethod(
                request.getMethod(), request.getEnabled(), usePlatformConfig, request.getConfigJson());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }
}
