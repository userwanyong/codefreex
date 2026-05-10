package cn.wanyj.codefreex.service.strategy.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * HTML 单文件持久化策略
 *
 * @author wanyj
 */
@Slf4j
@Component
public class HtmlSingleFileStrategy implements CodePersistStrategy {

    @Override
    public CodeGenType getCodeGenType() {
        return CodeGenType.HTML;
    }

    @Override
    public String persist(String deployKey, String code) {
        try {
            Path dirPath = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
            Files.createDirectories(dirPath);

            Path filePath = dirPath.resolve("index.html");
            Files.writeString(filePath, code, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("代码持久化成功: {}", filePath.toAbsolutePath());
            return filePath.toString();
        } catch (IOException e) {
            log.error("代码持久化失败: deployKey={}", deployKey, e);
            throw new RuntimeException("代码保存失败: " + e.getMessage(), e);
        }
    }
}
