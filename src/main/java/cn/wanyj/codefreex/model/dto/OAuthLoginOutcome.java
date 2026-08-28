package cn.wanyj.codefreex.model.dto;

import cn.wanyj.codefreex.model.dto.response.TokenResponse;
import lombok.Data;

/**
 * OAuth 回调处理结果：
 * 登录流程——老用户直接登录（token 生效）；新用户进入待完成状态（tempToken 生效，需补邀请码）；
 * 绑定流程——已登录用户绑定第三方账号，仅返回成功/失败与提示。
 *
 * @author wanyj
 */
@Data
public class OAuthLoginOutcome {

    /**
     * 是否为新用户待完成注册
     */
    private boolean pending;

    /**
     * 新用户临时令牌（Redis 暂存，10 分钟有效）
     */
    private String tempToken;

    /**
     * 新用户昵称（OAuth 提供方）
     */
    private String nickname;

    /**
     * 新用户头像（OAuth 提供方）
     */
    private String avatar;

    /**
     * 老用户登录令牌
     */
    private TokenResponse token;

    /**
     * 失败原因（登录/绑定失败时）
     */
    private String message;

    /**
     * 是否为绑定流程回调
     */
    private boolean bind;

    /**
     * 绑定流程结果
     */
    private boolean bindSuccess;

    public static OAuthLoginOutcome ofToken(TokenResponse token) {
        OAuthLoginOutcome outcome = new OAuthLoginOutcome();
        outcome.setPending(false);
        outcome.setToken(token);
        return outcome;
    }

    public static OAuthLoginOutcome ofPending(String tempToken, String nickname, String avatar) {
        OAuthLoginOutcome outcome = new OAuthLoginOutcome();
        outcome.setPending(true);
        outcome.setTempToken(tempToken);
        outcome.setNickname(nickname);
        outcome.setAvatar(avatar);
        return outcome;
    }

    public static OAuthLoginOutcome ofFailed(String message) {
        OAuthLoginOutcome outcome = new OAuthLoginOutcome();
        outcome.setPending(false);
        outcome.setMessage(message);
        return outcome;
    }

    public static OAuthLoginOutcome ofBind(boolean success, String message) {
        OAuthLoginOutcome outcome = new OAuthLoginOutcome();
        outcome.setBind(true);
        outcome.setBindSuccess(success);
        outcome.setMessage(message);
        return outcome;
    }
}
