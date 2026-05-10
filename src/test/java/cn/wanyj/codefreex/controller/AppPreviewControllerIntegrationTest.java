package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author BanXia
 */
@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class AppPreviewControllerIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MockMvc mockMvc;

    private final String previewKey = "P6_PREVIEW_KEY";
    private final String deployKey = "P6_DEPLOY_KEY";

    @AfterEach
    void tearDown() throws IOException {
        deleteIfExists(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, previewKey));
        deleteIfExists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
    }

    @Test
    void preview_indexPage_returnsHtml() throws Exception {
        Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, previewKey);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), "<html>preview page</html>");

        mockMvc.perform(get("/static/{deployKey}/", previewKey))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("preview page")));
    }

    @Test
    void deployed_staticAsset_returnsCss() throws Exception {
        Path deployDir = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        Files.createDirectories(deployDir.resolve("assets"));
        Files.writeString(deployDir.resolve("assets").resolve("main.css"), "body{color:red;}");

        mockMvc.perform(get("/deploy/{deployKey}/assets/main.css", deployKey))
                .andExpect(status().isOk())
                .andExpect(content().string("body{color:red;}"));
    }

    @Test
    void preview_jsAsset_returnsScriptContent() throws Exception {
        Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, previewKey);
        Files.createDirectories(outputDir.resolve("assets"));
        Files.writeString(outputDir.resolve("assets").resolve("main.js"), "console.log('preview');");

        mockMvc.perform(get("/static/{deployKey}/assets/main.js", previewKey))
                .andExpect(status().isOk())
                .andExpect(content().string("console.log('preview');"));
    }

    @Test
    void deployed_imageAsset_returnsBytes() throws Exception {
        Path deployDir = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        Files.createDirectories(deployDir.resolve("assets"));
        byte[] pngHeader = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};
        Files.write(deployDir.resolve("assets").resolve("logo.png"), pngHeader);

        mockMvc.perform(get("/deploy/{deployKey}/assets/logo.png", deployKey))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pngHeader));
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
