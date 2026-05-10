package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.AppDeployResponse;

import java.nio.file.Path;

/**
 * 应用部署服务
 *
 * @author BanXia
 */
public interface AppDeployService {

    AppDeployResponse deployApp(Long userId, Long appId);

    void cancelDeploy(Long userId, Long appId);

    DownloadArchive prepareDownloadArchive(Long userId, Long appId);

    record DownloadArchive(Path archivePath, String fileName) {
    }
}
