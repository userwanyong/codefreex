package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 租户级登录方式配置保存请求（与 auth-service 控制台能力一致）
 *
 * @author wanyj
 */
@Data
public class LoginMethodSaveRequest {

    @NotBlank(message = "登录方式不能为空")
    private String method;

    /** 是否启用：0-否，1-是 */
    @NotNull(message = "启用状态不能为空")
    private Integer enabled;

    /** 1=使用平台默认凭证，0=使用自身凭证 */
    private Integer usePlatformConfig;

    /** 自身凭证 JSON（usePlatformConfig=0 时生效，空表示不修改） */
    private String configJson;
}
