package cn.wanyj.codefreex.service;

import org.springframework.core.io.Resource;

import java.nio.file.Path;

/**
 * 应用文件存储服务
 *
 * @author BanXia
 */
public interface AppStorageService {

    Resource loadGeneratedResource(String deployKey, String relativePath);

    Resource loadDeployedResource(String deployKey, String relativePath);

    void copyGeneratedToDeployed(String deployKey);

    void deleteGeneratedFiles(String deployKey);

    void deleteDeployedFiles(String deployKey);

    Path generateNginxConfig(String deployKey);

    void deleteNginxConfig(String deployKey);

    Path createDownloadArchive(String deployKey, String archiveName);

    String generateCover(String deployKey, String appName, String description);
}
