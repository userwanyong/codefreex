package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class WechatQrCodeStatusRequest {

    @NotBlank(message = "二维码ID不能为空")
    private String qrcodeId;
}
