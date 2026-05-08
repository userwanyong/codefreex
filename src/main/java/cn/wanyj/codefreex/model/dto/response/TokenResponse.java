package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * @author wanyj
 */
@Data
public class TokenResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType;

    private Long expiresIn;
}
