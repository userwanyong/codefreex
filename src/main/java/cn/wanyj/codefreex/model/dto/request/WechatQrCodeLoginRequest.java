package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class WechatQrCodeLoginRequest {

    @NotBlank(message = "ticket不能为空")
    private String ticket;
}
