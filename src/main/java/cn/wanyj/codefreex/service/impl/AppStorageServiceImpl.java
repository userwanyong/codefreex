package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.service.AppStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 应用文件存储服务实现
 *
 * @author BanXia
 */
@Slf4j
@Service
public class AppStorageServiceImpl implements AppStorageService {

    private static final String INDEX_HTML = "index.html";
    private static final String COVER_FILE_NAME = "cover.png";
    private static final Set<String> ZIP_EXCLUDES = Set.of("node_modules", "dist", ".git", ".env");

    @Override
    public Resource loadGeneratedResource(String deployKey, String relativePath) {
        return loadResource(resolveGeneratedDir(deployKey), relativePath);
    }

    @Override
    public Resource loadDeployedResource(String deployKey, String relativePath) {
        return loadResource(resolveDeployedDir(deployKey), relativePath);
    }

    @Override
    public void copyGeneratedToDeployed(String deployKey) {
        Path sourceDir = resolveGeneratedDir(deployKey);
        Path targetDir = resolveDeployedDir(deployKey);
        ensureIndexFileExists(sourceDir);

        try {
            deleteDirectory(targetDir);
            Files.createDirectories(targetDir);
            try (var stream = Files.walk(sourceDir)) {
                stream.forEach(source -> copyPath(sourceDir, targetDir, source));
            }
        } catch (IOException e) {
            throw new RuntimeException("部署应用文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteGeneratedFiles(String deployKey) {
        deleteDirectory(resolveGeneratedDir(deployKey));
    }

    @Override
    public void deleteDeployedFiles(String deployKey) {
        deleteDirectory(resolveDeployedDir(deployKey));
    }

    @Override
    public Path generateNginxConfig(String deployKey) {
        Path deployedDir = resolveDeployedDir(deployKey);
        ensureIndexFileExists(deployedDir);

        try {
            Path nginxDir = Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d");
            Files.createDirectories(nginxDir);
            Path configPath = nginxDir.resolve(deployKey + ".conf");
            String location = deployedDir.toAbsolutePath().toString().replace("\\", "/");
            String config = """
                    server {
                        listen 80;
                        server_name %s.local;
                        location / {
                            root %s;
                            index index.html;
                            try_files $uri $uri/ /index.html;
                        }
                    }
                    """.formatted(deployKey, location);
            Files.writeString(configPath, config);
            return configPath;
        } catch (IOException e) {
            throw new RuntimeException("生成 Nginx 配置失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteNginxConfig(String deployKey) {
        Path configPath = Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d", deployKey + ".conf");
        try {
            Files.deleteIfExists(configPath);
        } catch (IOException e) {
            log.warn("删除 Nginx 配置失败: {}", configPath, e);
        }
    }

    @Override
    public Path createDownloadArchive(String deployKey, String archiveName) {
        Path sourceDir = resolveGeneratedDir(deployKey);
        ensureIndexFileExists(sourceDir);

        try {
            Path downloadRoot = Path.of(AppConstant.CODE_DOWNLOAD_ROOT_DIR);
            Files.createDirectories(downloadRoot);
            Path archivePath = downloadRoot.resolve(archiveName);

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(archivePath))) {
                try (var stream = Files.walk(sourceDir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> !shouldExclude(path, sourceDir))
                            .forEach(path -> writeZipEntry(sourceDir, path, zipOutputStream));
                }
            }
            return archivePath;
        } catch (IOException e) {
            throw new RuntimeException("创建下载压缩包失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateCover(String deployKey, String appName, String description) {
        Path deployedDir = resolveDeployedDir(deployKey);
        if (!Files.exists(deployedDir)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "部署目录不存在");
        }

        Path coverPath = deployedDir.resolve(COVER_FILE_NAME);
        try {
            BufferedImage image = new BufferedImage(1200, 630, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gradient = new GradientPaint(0, 0, new Color(14, 116, 144), 1200, 630, new Color(15, 23, 42));
            graphics.setPaint(gradient);
            graphics.fillRect(0, 0, 1200, 630);

            graphics.setColor(new Color(255, 255, 255, 28));
            graphics.fill(new RoundRectangle2D.Double(80, 70, 1040, 490, 36, 36));

            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 52));
            graphics.drawString(safeText(appName, "CodeFreeX App"), 100, 190);

            graphics.setFont(new Font("SansSerif", Font.PLAIN, 28));
            drawMultiline(graphics, safeText(description, "AI generated application"), 100, 260, 920, 42);

            graphics.setFont(new Font("SansSerif", Font.BOLD, 22));
            graphics.setColor(new Color(191, 219, 254));
            graphics.drawString("DEPLOYED", 100, 530);
            graphics.drawString("/api/deploy/" + deployKey + "/", 100, 570);
            graphics.dispose();

            ImageIO.write(image, "png", coverPath.toFile());
            return "/api/deploy/" + deployKey + "/" + COVER_FILE_NAME;
        } catch (IOException e) {
            throw new RuntimeException("生成封面失败: " + e.getMessage(), e);
        }
    }

    private Resource loadResource(Path rootDir, String relativePath) {
        Path targetPath = resolveResourcePath(rootDir, relativePath);
        if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "资源不存在");
        }
        return new FileSystemResource(targetPath);
    }

    private Path resolveResourcePath(Path rootDir, String relativePath) {
        Path normalizedRoot = rootDir.normalize();
        if (!Files.exists(normalizedRoot)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "资源目录不存在");
        }

        String relative = relativePath == null ? "" : relativePath.replace("\\", "/");
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank()) {
            relative = INDEX_HTML;
        }

        Path candidate = normalizedRoot.resolve(relative).normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "非法资源路径");
        }
        if (Files.isDirectory(candidate)) {
            candidate = candidate.resolve(INDEX_HTML).normalize();
        }
        return candidate;
    }

    private void ensureIndexFileExists(Path sourceDir) {
        Path indexPath = sourceDir.resolve(INDEX_HTML);
        if (!Files.exists(indexPath)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "生成目录不存在可预览页面");
        }
    }

    private void copyPath(Path sourceRoot, Path targetRoot, Path source) {
        try {
            Path relative = sourceRoot.relativize(source);
            Path target = targetRoot.resolve(relative);
            if (Files.isDirectory(source)) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("复制应用文件失败: " + e.getMessage(), e);
        }
    }

    private boolean shouldExclude(Path path, Path sourceRoot) {
        Path relative = sourceRoot.relativize(path);
        for (Path segment : relative) {
            if (ZIP_EXCLUDES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private void writeZipEntry(Path sourceRoot, Path file, ZipOutputStream zipOutputStream) {
        Path relative = sourceRoot.relativize(file);
        try {
            zipOutputStream.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
            Files.copy(file, zipOutputStream);
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            throw new RuntimeException("写入压缩包失败: " + e.getMessage(), e);
        }
    }

    private void drawMultiline(Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics metrics = graphics.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int currentY = y;
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                graphics.drawString(line.toString(), x, currentY);
                line = new StringBuilder(word);
                currentY += lineHeight;
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            graphics.drawString(line.toString(), x, currentY);
        }
    }

    private String safeText(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text;
    }

    private Path resolveGeneratedDir(String deployKey) {
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
    }

    private Path resolveDeployedDir(String deployKey) {
        return Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.warn("删除文件失败: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("删除目录失败: {}", directory, e);
        }
    }
}
