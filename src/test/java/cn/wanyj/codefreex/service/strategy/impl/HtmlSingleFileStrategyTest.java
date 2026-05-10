package cn.wanyj.codefreex.service.strategy.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlSingleFileStrategyTest {

    private final HtmlSingleFileStrategy strategy = new HtmlSingleFileStrategy();

    @Test
    void persist_validCode_createsDirectoryAndFile(@TempDir Path tempDir) throws IOException {
        // AppConstant.CODE_OUTPUT_ROOT_DIR is a static final, so we test with the real path
        // and clean up after. This is acceptable for a unit test of the strategy.
        String deployKey = "test_persist_" + System.currentTimeMillis();
        String code = "<!DOCTYPE html><html><body>Hello</body></html>";

        String resultPath = strategy.persist(deployKey, code);

        assertThat(resultPath).isNotNull();
        Path filePath = Path.of(resultPath);
        assertThat(Files.exists(filePath)).isTrue();
        assertThat(Files.readString(filePath)).isEqualTo(code);

        // Cleanup
        Files.deleteIfExists(filePath);
        Files.deleteIfExists(filePath.getParent());
    }

    @Test
    void persist_overwritesExistingFile() throws IOException {
        String deployKey = "test_overwrite_" + System.currentTimeMillis();
        String code1 = "<html>v1</html>";
        String code2 = "<html>v2</html>";

        String path1 = strategy.persist(deployKey, code1);
        String path2 = strategy.persist(deployKey, code2);

        assertThat(path1).isEqualTo(path2);
        assertThat(Files.readString(Path.of(path2))).isEqualTo(code2);

        // Cleanup
        Files.deleteIfExists(Path.of(path2));
        Files.deleteIfExists(Path.of(path2).getParent());
    }
}
