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

/**
 * 鏈湴鏂囦欢宸ュ叿瀹炵幇
 *
 * @author BanXia
 */
@Service
public class LocalWorkflowFileToolService implements WorkflowFileToolService {

    @Override
    public void writeFile(Path rootDir, String relativePath, String content) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("鍐欏叆鏂囦欢澶辫触: " + relativePath, e);
        }
    }

    @Override
    public void editFile(Path rootDir, String relativePath, String originalContent, String newContent) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            String content = Files.readString(file);
            if (!content.contains(originalContent)) {
                throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "鏈壘鍒板緟淇敼鍐呭");
            }
            Files.writeString(file, content.replace(originalContent, newContent), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("淇敼鏂囦欢澶辫触: " + relativePath, e);
        }
    }

    @Override
    public void deleteFile(Path rootDir, String relativePath) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("鍒犻櫎鏂囦欢澶辫触: " + relativePath, e);
        }
    }

    @Override
    public List<String> listFiles(Path rootDir) {
        if (!Files.exists(rootDir)) {
            return List.of();
        }
        try (var stream = Files.walk(rootDir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .map(rootDir::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("鍒楀嚭鏂囦欢澶辫触", e);
        }
    }

    @Override
    public String readFile(Path rootDir, String relativePath) {
        Path file = resolvePath(rootDir, relativePath);
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("璇诲彇鏂囦欢澶辫触: " + relativePath, e);
        }
    }

    private Path resolvePath(Path rootDir, String relativePath) {
        Path resolved = rootDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDir.normalize())) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "闈炴硶鏂囦欢璺緞");
        }
        return resolved;
    }
}
