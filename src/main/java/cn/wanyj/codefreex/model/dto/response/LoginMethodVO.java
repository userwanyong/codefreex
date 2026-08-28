package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 租户级登录方式配置视图对象（来源 auth-service RPC，凭证不回传）
 *
 * @author wanyj
 */
@Data
public class LoginMethodVO {

    /** 登录方式 code，如 oauth:gitee */
    private String method;

    /** 类别：password / email / sms / oauth */
    private String category;

    private String displayName;

    /** 本租户是否启用：0-否，1-是 */
    private Integer enabled;

    /** 1=使用平台默认凭证，0=使用自身凭证 */
    private Integer usePlatformConfig;

    /** 生效凭证是否已配置 */
    private Boolean hasConfig;

    /** 平台是否已开启该方式（平台未开启时租户不可启用） */
    private Boolean platformEnabled;
}
