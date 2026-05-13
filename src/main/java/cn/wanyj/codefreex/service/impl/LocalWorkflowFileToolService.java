package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.service.WorkflowFileToolService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 本地文件工具实现
 *
 * @author BanXia
 */
@Service
public class LocalWorkflowFileToolService implements WorkflowFileToolService {

    private static final Set<String> EXCLUDED_DIRS = Set.of("node_modules", ".git", "dist", ".vite");
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".bin", ".png", ".jpg", ".jpeg", ".gif",
            ".ico", ".svg", ".woff", ".woff2", ".ttf", ".eot", ".map", ".wasm"
    );

    @Override
    public void writeFile(Path rootDir, String relativePath, String content) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + relativePath, e);
        }
    }

    @Override
    public void editFile(Path rootDir, String relativePath, String originalContent, String newContent) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            String content = Files.readString(file);
            if (!content.contains(originalContent)) {
                throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "未找到待修改内容");
            }
            Files.writeString(file, content.replace(originalContent, newContent), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("修改文件失败: " + relativePath, e);
        }
    }

    @Override
    public void deleteFile(Path rootDir, String relativePath) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("删除文件失败: " + relativePath, e);
        }
    }

    @Override
    public List<String> listFiles(Path rootDir) {
        if (!Files.exists(rootDir)) {
            return List.of();
        }
        try (var stream = Files.walk(rootDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        for (Path segment : rootDir.relativize(p)) {
                            if (EXCLUDED_DIRS.contains(segment.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        int dotIndex = name.lastIndexOf('.');
                        if (dotIndex >= 0 && BINARY_EXTENSIONS.contains(name.substring(dotIndex))) {
                            return false;
                        }
                        return true;
                    })
                    .sorted(Comparator.naturalOrder())
                    .map(rootDir::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("列出文件失败", e);
        }
    }

    @Override
    public String readFile(Path rootDir, String relativePath) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + relativePath, e);
        }
    }

    private Path resolvePath(Path rootDir, String relativePath) {
        Path resolved = rootDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDir.normalize())) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "非法文件路径");
        }
        return resolved;
    }
}
