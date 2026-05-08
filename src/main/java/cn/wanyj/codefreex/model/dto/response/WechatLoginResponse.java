package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class WechatLoginResponse {

    /**
     * 是否为新用户
     */
    private boolean newUser;

    /**
     * 临时令牌（新用户时返回，用于后续完成注册）
     */
    private String tempToken;

    /**
     * 登录令牌（老用户时直接返回）
     */
    private TokenResponse token;

    /**
     * 微信用户昵称（新用户时返回，前端可展示）
     */
    private String nickname;

    /**
     * 微信用户头像（新用户时返回，前端可展示）
     */
    private String avatar;
}
