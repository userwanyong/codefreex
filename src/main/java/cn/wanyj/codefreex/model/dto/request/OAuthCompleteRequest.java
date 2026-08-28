package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth 新用户完成注册请求（提交邀请码）
 *
 * @author wanyj
 */
@Data
public class OAuthCompleteRequest {

    /**
     * OAuth 回调后颁发的临时令牌
     */
    @NotBlank(message = "临时令牌不能为空")
    private String tempToken;

    /**
     * 邀请码
     */
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
