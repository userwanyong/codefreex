package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件工具服务
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
