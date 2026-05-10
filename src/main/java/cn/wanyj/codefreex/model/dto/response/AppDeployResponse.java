package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用部署响应
 *
 * @author BanXia
 */
@Data
public class AppDeployResponse {

    private Long appId;

    private String deployKey;

    private String status;

    private String previewUrl;

    private String deployedUrl;

    private String coverUrl;

    private LocalDateTime deployedTime;
}
