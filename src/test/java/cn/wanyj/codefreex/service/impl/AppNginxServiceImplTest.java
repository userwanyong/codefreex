package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.CommandExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * @author BanXia
 */
@ExtendWith(MockitoExtension.class)
class AppNginxServiceImplTest {

    @Mock
    private CommandExecutor commandExecutor;

    private final String deployKey = "P6_NGINX_KEY";

    @AfterEach
    void tearDown() throws IOException {
        deleteIfExists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
        deleteIfExists(Path.of(AppConstant.CODE_NGINX_ROOT_DIR));
    }

    @Test
    void publish_whenReloadEnabled_generatesConfigAndExecutesReload() throws Exception {
        Path deployDir = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        Files.createDirectories(deployDir);
        Files.writeString(deployDir.resolve("index.html"), "<html>nginx</html>");

        AppRuntimeConfig.NginxProperties properties = new AppRuntimeConfig.NginxProperties();
        properties.setEnabled(true);
        properties.setReloadOnDeploy(true);
        properties.setBinaryPath("C:/nginx/nginx.exe");
        AppNginxServiceImpl service = new AppNginxServiceImpl(properties, commandExecutor);

        Path configPath = service.publish(deployKey);

        assertThat(Files.exists(configPath)).isTrue();
        assertThat(Files.readString(configPath)).contains("server_name " + deployKey + ".local");
        verify(commandExecutor).execute(eq(java.util.List.of("C:/nginx/nginx.exe", "-s", "reload")), any());
    }

    @Test
    void remove_whenReloadEnabled_deletesConfigAndExecutesReload() throws Exception {
        Path confDir = Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d");
        Files.createDirectories(confDir);
        Path configPath = confDir.resolve(deployKey + ".conf");
        Files.writeString(configPath, "server {}");

        AppRuntimeConfig.NginxProperties properties = new AppRuntimeConfig.NginxProperties();
        properties.setEnabled(true);
        properties.setReloadOnDeploy(true);
        properties.setBinaryPath("C:/nginx/nginx.exe");
        AppNginxServiceImpl service = new AppNginxServiceImpl(properties, commandExecutor);

        service.remove(deployKey);

        assertThat(Files.exists(configPath)).isFalse();
        verify(commandExecutor).execute(eq(java.util.List.of("C:/nginx/nginx.exe", "-s", "reload")), any());
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
