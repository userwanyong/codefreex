package cn.wanyj.codefreex.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置
 *
 * @author BanXia
 */
@Configuration
public class OssConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.oss")
    public OssProperties ossProperties() {
        return new OssProperties();
    }

    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssProperties ossProperties) {
        return new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }

    @Data
    public static class OssProperties {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
        /** 自定义域名前缀，不配置则使用默认格式 https://{bucket}.{endpoint} */
        private String urlPrefix;
    }
}
