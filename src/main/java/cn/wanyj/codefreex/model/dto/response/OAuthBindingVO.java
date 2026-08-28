package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 第三方账号绑定视图对象（来源 auth-service RPC）
 *
 * @author wanyj
 */
@Data
public class OAuthBindingVO {

    /** 提供方：gitee / github */
    private String provider;

    /** 提供方用户唯一ID */
    private String providerUid;

    private LocalDateTime createTime;
}
