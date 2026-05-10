package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.CommandExecutor;
import cn.wanyj.codefreex.service.ProjectBuildService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vue 椤圭洰鏋勫缓鏈嶅姟瀹炵幇
 *
 * @author BanXia
 */
@Service
@RequiredArgsConstructor
public class ProjectBuildServiceImpl implements ProjectBuildService {

    private final CommandExecutor commandExecutor;
    private final AppRuntimeConfig.WorkflowProperties workflowProperties;

    @Override
    public void buildVueProject(Path generatedRootDir, Consumer<String> progressConsumer) {
        Path sourceDir = generatedRootDir.resolve("source");
        if (!Files.exists(sourceDir)) {
            throw new RuntimeException("Vue 宸ョ▼婧愮爜鐩綍涓嶅瓨鍦?");
        }

        if (progressConsumer != null) {
            progressConsumer.accept("npm_install");
        }
        if (workflowProperties.isVueBuildEnabled()) {
            commandExecutor.execute(List.of(workflowProperties.getNpmCommand(), "install"), sourceDir);
            if (progressConsumer != null) {
                progressConsumer.accept("npm_build");
            }
            commandExecutor.execute(List.of(workflowProperties.getNpmCommand(), "run", "build"), sourceDir);
        }

        Path distDir = sourceDir.resolve("dist");
        if (!Files.exists(distDir)) {
            writeFallbackPreview(generatedRootDir);
            return;
        }
        mirrorDirectory(distDir, generatedRootDir);
    }

    private void writeFallbackPreview(Path generatedRootDir) {
        try {
            Files.writeString(generatedRootDir.resolve("index.html"), """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Vue Build Pending</title>
                    </head>
                    <body>
                      <div id="app">Vue build output unavailable. Source files were generated successfully.</div>
                    </body>
                    </html>
                    """);
        } catch (IOException e) {
            throw new RuntimeException("鍐欏叆 Vue 棰勮椤甸潰澶辫触", e);
        }
    }

    private void mirrorDirectory(Path sourceDir, Path targetDir) {
        try (var cleanup = Files.list(targetDir)) {
            cleanup.filter(path -> !path.getFileName().toString().equals("source"))
                    .forEach(path -> deletePath(path));
        } catch (IOException e) {
            throw new RuntimeException("娓呯悊 Vue 棰勮鐩綍澶辫触", e);
        }

        try (var stream = Files.walk(sourceDir)) {
            stream.forEach(path -> {
                try {
                    Path relative = sourceDir.relativize(path);
                    Path target = targetDir.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("鍚屾 Vue 鏋勫缓浜х墿澶辫触", e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("璇诲彇 Vue 鏋勫缓浜х墿澶辫触", e);
        }
    }

    private void deletePath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.sorted(Comparator.reverseOrder()).forEach(this::deletePath);
                }
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("鍒犻櫎鏂囦欢澶辫触", e);
        }
    }
}
