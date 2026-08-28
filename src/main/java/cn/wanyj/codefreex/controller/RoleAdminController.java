package cn.wanyj.codefreex.controller;

import cn.wanyj.auth.api.protobuf.OperationResult;
import cn.wanyj.auth.api.protobuf.RoleRpcResponse;
import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.dto.request.RolePermissionAssignRequest;
import cn.wanyj.codefreex.model.dto.request.RoleSaveRequest;
import cn.wanyj.codefreex.model.dto.response.RoleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 角色管理接口（管理员侧）：全部委托 auth-service RPC，与认证服务控制台数据实时一致
 *
 * @author wanyj
 */
@Tag(name = "角色管理接口（管理员）")
@RestController
@RequestMapping("/role/admin")
@RequiredArgsConstructor
public class RoleAdminController {

    /** 内置角色不可删除（注册/授权依赖） */
    private static final Set<String> BUILT_IN_ROLE_CODES = Set.of("ROLE_ADMIN", "ROLE_USER", "ROLE_PLATFORM_ADMIN");

    private final AuthRpcClient authRpcClient;

    @Operation(summary = "查询全部角色（含权限编码）")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<RoleVO>> listRoles() {
        List<RoleVO> result = new ArrayList<>();
        for (RoleRpcResponse role : authRpcClient.getAllRoles()) {
            result.add(toRoleVO(role));
        }
        return ResultUtils.success(result);
    }

    @Operation(summary = "创建角色")
    @PostMapping("/create")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Long> createRole(@Valid @RequestBody RoleSaveRequest request) {
        if (request.getId() != null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "创建角色无需传入ID");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "角色编码不能为空");
        }
        RoleRpcResponse created = authRpcClient.createRole(
                request.getCode(), request.getName(), request.getDescription());
        if (created == null || created.getId() == 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "创建角色失败：编码可能已存在");
        }
        return ResultUtils.success(created.getId());
    }

    @Operation(summary = "更新角色（仅名称与描述）")
    @PostMapping("/update")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> updateRole(@Valid @RequestBody RoleSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "角色ID不能为空");
        }
        OperationResult result = authRpcClient.updateRole(request.getId(), request.getName(), request.getDescription());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "删除角色（内置角色不可删除）")
    @PostMapping("/{roleId}/delete")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> deleteRole(@PathVariable Long roleId) {
        for (RoleRpcResponse role : authRpcClient.getAllRoles()) {
            if (role.getId() == roleId && BUILT_IN_ROLE_CODES.contains(role.getCode())) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "内置角色 " + role.getCode() + " 不可删除");
            }
        }
        OperationResult result = authRpcClient.deleteRole(roleId);
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    @Operation(summary = "为角色分配权限（全量替换）")
    @PostMapping("/permissions")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> assignPermissions(@Valid @RequestBody RolePermissionAssignRequest request) {
        OperationResult result = authRpcClient.assignRolePermissions(request.getRoleId(), request.getPermissionIds());
        if (!result.getSuccess()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, result.getMessage());
        }
        return ResultUtils.success(true);
    }

    private RoleVO toRoleVO(RoleRpcResponse role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setPermissions(new ArrayList<>(role.getPermissionsList()));
        vo.setBuiltIn(BUILT_IN_ROLE_CODES.contains(role.getCode()));
        return vo;
    }
}
