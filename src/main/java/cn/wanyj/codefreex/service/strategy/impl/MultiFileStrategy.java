package cn.wanyj.codefreex.service.strategy.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.WorkflowFileToolService;
import cn.wanyj.codefreex.service.impl.FileBundleParser;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Multi-file persist strategy
 *
 * @author BanXia
 */
@Component
@RequiredArgsConstructor
public class MultiFileStrategy implements CodePersistStrategy {

    private final WorkflowFileToolService workflowFileToolService;

    @Override
    public CodeGenType getCodeGenType() {
        return CodeGenType.MULTI_FILE;
    }

    @Override
    public String persist(String deployKey, String code) {
        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        try {
            Files.createDirectories(rootDir);
        } catch (Exception e) {
            throw new RuntimeException("Create multi-file directory failed", e);
        }

        Map<String, String> fileMap = FileBundleParser.parse(code);
        if (fileMap.isEmpty()) {
            workflowFileToolService.writeFile(rootDir, "index.html", code);
            return rootDir.toString();
        }
        for (Map.Entry<String, String> entry : fileMap.entrySet()) {
            workflowFileToolService.writeFile(rootDir, entry.getKey(), entry.getValue());
        }
        return rootDir.toString();
    }
}
