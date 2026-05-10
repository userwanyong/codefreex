package cn.wanyj.codefreex.service.strategy.impl;

import cn.wanyj.codefreex.service.WorkflowFileToolService;
import cn.wanyj.codefreex.service.impl.LocalWorkflowFileToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MultiFileStrategyTest {

    @TempDir
    Path tempDir;

    @Test
    void persist_writesParsedFileBlocks() throws Exception {
        WorkflowFileToolService fileToolService = new LocalWorkflowFileToolService();
        MultiFileStrategy strategy = new MultiFileStrategy(fileToolService);
        String deployKey = "mf_test";
        String content = """
                ```file:index.html
                <html>ok</html>
                ```
                ```file:assets/app.css
                body{}
                ```
                """;

        strategy.persist(deployKey, content);

        Path root = Path.of(System.getProperty("user.dir"), "tmp/code_output", deployKey);
        assertThat(Files.readString(root.resolve("index.html"))).contains("ok");
        assertThat(Files.readString(root.resolve("assets").resolve("app.css"))).contains("body");
    }
}
