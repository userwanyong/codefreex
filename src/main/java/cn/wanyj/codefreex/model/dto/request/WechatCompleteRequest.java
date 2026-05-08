package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class WechatCompleteRequest {

    @NotBlank(message = "临时令牌不能为空")
    private String tempToken;

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
