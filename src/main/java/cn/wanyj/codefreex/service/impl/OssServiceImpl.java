package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.OssConfig;
import cn.wanyj.codefreex.service.OssService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 阿里云 OSS 存储服务实现
 *
 * @author BanXia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {

    private final OSS ossClient;
    private final OssConfig.OssProperties ossProperties;

    @Override
    public String upload(String objectKey, Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(Files.size(filePath));
            metadata.setContentType(guessContentType(objectKey));
            ossClient.putObject(ossProperties.getBucket(), objectKey, inputStream, metadata);
            log.info("Uploaded to OSS: bucket={}, key={}", ossProperties.getBucket(), objectKey);
            return buildUrl(objectKey);
        } catch (Exception e) {
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String upload(String objectKey, byte[] data, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(data.length);
            metadata.setContentType(contentType);
            ossClient.putObject(ossProperties.getBucket(), objectKey,
                    new ByteArrayInputStream(data), metadata);
            log.info("Uploaded to OSS: bucket={}, key={}", ossProperties.getBucket(), objectKey);
            return buildUrl(objectKey);
        } catch (Exception e) {
            throw new RuntimeException("OSS upload failed: " + e.getMessage(), e);
        }
    }

    private String buildUrl(String objectKey) {
        String prefix = ossProperties.getUrlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return prefix + "/" + objectKey;
        }
        return "https://" + ossProperties.getBucket() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    private String guessContentType(String key) {
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return "image/jpeg";
        if (key.endsWith(".gif")) return "image/gif";
        if (key.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
