package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理员创建用户请求（在 auth-service 中注册，规则与 auth-service 控制台一致）
 *
 * @author wanyj
 */
@Data
public class UserAdminCreateRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 50, message = "账号长度须在 3-50 之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号仅支持字母、数字、下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度须在 6-50 之间")
    private String password;

    private String nickname;

    private String email;

    /** 创建后分配的角色 ID 列表（为空时默认 ROLE_USER） */
    private List<Long> roleIds;
}
