package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.AuthRpcClient;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.LoginUserContext;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author wanyj
 */
@Tag(name = "用户接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserInfoService userInfoService;
    private final AuthRpcClient authRpcClient;

    @Operation(summary = "获取用户关联信息")
    @GetMapping("/info")
    @AuthCheck
    public BaseResponse<UserInfo> getUserInfo() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(userInfoService.getUserInfo(userId));
    }

    @Operation(summary = "获取用户角色")
    @GetMapping("/role")
    @AuthCheck
    public BaseResponse<java.util.List<String>> getUserRoles() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(authRpcClient.getUserRoles(userId));
    }
}
