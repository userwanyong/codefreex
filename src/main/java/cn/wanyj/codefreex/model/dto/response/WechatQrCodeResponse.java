package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class WechatQrCodeResponse {

    private String qrcodeId;

    private String qrCodeUrl;

    /**
     * 状态：PENDING / SCANNED / CONFIRMED / EXPIRED
     */
    private String status;

    /**
     * 扫码后的 ticket（CONFIRMED 状态时返回）
     */
    private String ticket;

    /**
     * 扫码用户的显示名
     */
    private String displayName;

    /**
     * 扫码用户的头像
     */
    private String photo;
}
