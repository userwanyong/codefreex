package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 鏂囦欢宸ュ叿鏈嶅姟
 *
 * @author BanXia
 */
public interface WorkflowFileToolService {

    void writeFile(Path rootDir, String relativePath, String content);

    void editFile(Path rootDir, String relativePath, String originalContent, String newContent);

    void deleteFile(Path rootDir, String relativePath);

    List<String> listFiles(Path rootDir);

    String readFile(Path rootDir, String relativePath);
}
