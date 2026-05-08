package cn.wanyj.codefreex.config;

import cn.authing.sdk.java.client.AuthenticationClient;
import cn.authing.sdk.java.model.AuthenticationClientOptions;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.text.ParseException;

/**
 * @author wanyj
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "authing")
public class AuthingConfig {

    private String appId;
    private String appSecret;
    private String appHost;

    /**
     * 每次调用创建新的 AuthenticationClient 实例（线程安全）
     */
    public AuthenticationClient newAuthenticationClient() {
        try {
            AuthenticationClientOptions options = new AuthenticationClientOptions();
            options.setAppId(appId);
            options.setAppSecret(appSecret);
            options.setAppHost(appHost);
            return new AuthenticationClient(options);
        } catch (IOException | ParseException e) {
            log.error("创建 Authing AuthenticationClient 失败", e);
            throw new RuntimeException("创建 Authing 客户端失败", e);
        }
    }
}
