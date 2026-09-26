package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * editFile 唯一匹配校验测试：防止 String.replace 全局替换损坏文件
 *
 * @author wanyj
 */
class LocalWorkflowFileToolServiceTest {

    @TempDir
    Path tempDir;

    private final LocalWorkflowFileToolService service = new LocalWorkflowFileToolService();

    @Test
    void editFile_uniqueMatch_replacesOnlyTargetOccurrence() throws Exception {
        Path file = tempDir.resolve("index.html");
        Files.writeString(file, "<div class=\"a\">one</div>\n<div class=\"b\">two</div>\n<div class=\"c\">one</div>");

        service.editFile(tempDir, "index.html", "<div class=\"b\">two</div>", "<div class=\"b\">changed</div>");

        String content = Files.readString(file);
        assertThat(content).contains("<div class=\"b\">changed</div>");
        // 其余出现处不受影响（旧实现 String.replace 会把两处 class=a/c 的内容一并波及）
        assertThat(content).contains("<div class=\"a\">one</div>");
        assertThat(content).contains("<div class=\"c\">one</div>");
    }

    @Test
    void editFile_multipleMatches_rejected() throws Exception {
        Path file = tempDir.resolve("app.js");
        Files.writeString(file, "const a = 1;\nconst b = 2;\nconst c = 3;");

        assertThatThrownBy(() -> service.editFile(tempDir, "app.js", "const ", "let "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3 处");
        assertThat(Files.readString(file)).contains("const a = 1;");
    }

    @Test
    void editFile_emptyOriginal_rejected() throws Exception {
        Path file = tempDir.resolve("index.html");
        Files.writeString(file, "<html></html>");

        assertThatThrownBy(() -> service.editFile(tempDir, "index.html", "", "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
        assertThat(Files.readString(file)).isEqualTo("<html></html>");
    }

    @Test
    void editFile_notFound_rejected() throws Exception {
        Path file = tempDir.resolve("index.html");
        Files.writeString(file, "<html></html>");

        assertThatThrownBy(() -> service.editFile(tempDir, "index.html", "<body>", "<body id='app'>"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到");
    }

    @Test
    void editFile_nullNewContent_removesOriginal() throws Exception {
        Path file = tempDir.resolve("index.html");
        Files.writeString(file, "<html><meta temp><body></body></html>");

        service.editFile(tempDir, "index.html", "<meta temp>", null);

        assertThat(Files.readString(file)).isEqualTo("<html><body></body></html>");
    }

    @Test
    void editFile_pathTraversal_blocked() {
        assertThatThrownBy(() -> service.editFile(tempDir, "../outside.txt", "a", "b"))
                .isInstanceOf(BusinessException.class);
    }
}
