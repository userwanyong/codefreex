package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.service.AppNginxService;
import cn.wanyj.codefreex.service.CommandExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Nginx 发布服务实现
 *
 * @author BanXia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppNginxServiceImpl implements AppNginxService {

    private final AppRuntimeConfig.NginxProperties nginxProperties;
    private final CommandExecutor commandExecutor;

    @Override
    public Path publish(String deployKey) {
        Path deployedDir = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        Path indexPath = deployedDir.resolve("index.html");
        if (!Files.exists(indexPath)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "部署目录不存在首页文件");
        }

        try {
            Path confDir = resolveConfDir();
            Files.createDirectories(confDir);
            Path configPath = confDir.resolve(deployKey + ".conf");
            String config = """
                    server {
                        listen 80;
                        server_name %s%s;
                        location / {
                            root %s;
                            index index.html;
                            try_files $uri $uri/ /index.html;
                        }
                    }
                    """.formatted(deployKey, nginxProperties.getServerNameSuffix(),
                    deployedDir.toAbsolutePath().toString().replace("\\", "/"));
            Files.writeString(configPath, config);

            if (nginxProperties.isEnabled() && nginxProperties.isReloadOnDeploy()) {
                reloadNginx();
            }
            return configPath;
        } catch (IOException e) {
            throw new RuntimeException("生成 Nginx 配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void remove(String deployKey) {
        try {
            Files.deleteIfExists(resolveConfDir().resolve(deployKey + ".conf"));
            if (nginxProperties.isEnabled() && nginxProperties.isReloadOnDeploy()) {
                reloadNginx();
            }
        } catch (IOException e) {
            log.warn("删除 Nginx 配置失败: deployKey={}", deployKey, e);
        }
    }

    private void reloadNginx() {
        String binaryPath = nginxProperties.getBinaryPath();
        if (binaryPath == null || binaryPath.isBlank()) {
            throw new BusinessException(ResponseCode.SYSTEM_ERROR, "未配置 nginx 可执行文件路径");
        }
        commandExecutor.execute(List.of(binaryPath, "-s", "reload"), Path.of(binaryPath).getParent());
    }

    private Path resolveConfDir() {
        if (nginxProperties.getConfDir() != null && !nginxProperties.getConfDir().isBlank()) {
            return Path.of(nginxProperties.getConfDir());
        }
        return Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d");
    }
}
