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
 * Vue 项目构建服务实现
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
            throw new RuntimeException("Vue 工程源码目录不存在");
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
        validateDistOutput(distDir);
        mirrorDirectory(distDir, generatedRootDir);
    }

    /**
     * 构建产物有效性校验：Vite 以 index.html 为打包入口，若源 index.html 未声明
     * module script 入口标签，vite build 不报错但产物是空壳（dist 中没有任何 JS），
     * 预览必然白屏。此处将空壳产物判定为构建失败，交给自动修复循环补齐入口标签。
     */
    private void validateDistOutput(Path distDir) {
        boolean hasJs;
        try (var stream = Files.walk(distDir)) {
            hasJs = stream.filter(Files::isRegularFile)
                    .anyMatch(path -> {
                        String name = path.getFileName().toString().toLowerCase();
                        return name.endsWith(".js") || name.endsWith(".mjs");
                    });
        } catch (IOException e) {
            throw new RuntimeException("读取构建产物目录失败: " + e.getMessage(), e);
        }
        if (!hasJs) {
            throw new RuntimeException("""
                    构建产物校验失败: dist 目录中没有任何 JS 文件，vite build 未打包任何代码，页面将白屏。\
                    通常原因是 index.html 缺少 Vite 入口标签\
                    <script type="module" src="/src/main.js"></script>（缺少该标签时 vite build 不报错、退出码为 0）。\
                    请检查 index.html，在 body 末尾补齐入口标签（src 路径与实际的入口文件一致），不要改动其他文件。\
                    如入口文件名不是 src/main.js，以项目实际入口为准。""");
        }
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
            throw new RuntimeException("写入 Vue 预览页面失败", e);
        }
    }

    private void mirrorDirectory(Path sourceDir, Path targetDir) {
        try (var cleanup = Files.list(targetDir)) {
            cleanup.filter(path -> !path.getFileName().toString().equals("source"))
                    .forEach(path -> deletePath(path));
        } catch (IOException e) {
            throw new RuntimeException("清理 Vue 预览目录失败", e);
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
                    throw new RuntimeException("同步 Vue 构建产物失败", e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("读取 Vue 构建产物失败", e);
        }
    }

    private void deletePath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.filter(p -> !p.equals(path))
                          .sorted(Comparator.reverseOrder())
                          .forEach(this::deletePath);
                }
                Files.deleteIfExists(path);
            } else {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败", e);
        }
    }
}
