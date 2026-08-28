package cn.wanyj.codefreex.controller;

import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.PermissionRpcResponse;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.request.PermissionSaveRequest;
import cn.wanyj.codefreex.model.dto.response.PermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限管理接口（管理员侧）：全部委托 auth-service RPC
 *
 * @author wanyj
 */
@Tag(name = "权限管理接口（管理员）")
@RestController
@RequestMapping("/permission/admin")
@RequiredArgsConstructor
public class PermissionAdminController {

    private final AuthRpcClient authRpcClient;

    @Operation(summary = "查询全部权限")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<PermissionVO>> listPermissions() {
        List<PermissionVO> result = new ArrayList<>();
        for (PermissionRpcResponse permission : authRpcClient.getAllPermissions()) {
            PermissionVO vo = new PermissionVO();
            vo.setId(permission.getId());
            vo.setCode(permission.getCode());
            vo.setName(permission.getName());
            vo.setResource(permission.getResource());
            vo.setAction(permission.getAction());
            vo.setDescription(permission.getDescription());
            result.add(vo);
        }
        return ResultUtils.success(result);
    }

    @Operation(summary = "创建权限")
    @PostMapping("/create")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Long> createPermission(@Valid @RequestBody PermissionSaveRequest request) {
        PermissionRpcResponse created = authRpcClient.createPermission(
                request.getCode(), request.getName(), request.getResource(), request.getAction(), request.getDescription());
        if (created == null || created.getId() == 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "创建权限失败：编码可能已存在");
        }
        return ResultUtils.success(created.getId());
    }

    @Operation(summary = "删除权限（同时解除角色关联）")
    @PostMapping("/{permissionId}/delete")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> deletePermission(@PathVariable Long permissionId) {
        OperationResult result = authRpcClient.deletePermission(permissionId);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }
}
