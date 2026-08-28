package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员编辑用户请求（null 字段不更新；邮箱/手机传空串表示清空）
 *
 * @author wanyj
 */
@Data
public class UserAdminUpdateRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String username;

    private String password;

    private String email;

    private String phone;

    private String nickname;

    private String avatar;

    /** 1-正常，0-禁用 */
    private Integer status;
}
