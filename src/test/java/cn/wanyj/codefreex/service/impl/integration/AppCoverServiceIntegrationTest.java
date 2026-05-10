package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.service.AppCoverService;
import cn.wanyj.codefreex.service.ScreenshotExecutor;
import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/**
 * @author BanXia
 */
@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class, properties = "app.screenshot.enabled=true")
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class AppCoverServiceIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private ScreenshotExecutor screenshotExecutor;

    @Autowired
    private AppCoverService appCoverService;

    @Autowired
    private AppMapper appMapper;

    private final Long appId = 1001L;
    private final String deployKey = "DK_TEST_001";

    @AfterEach
    void tearDown() throws IOException {
        deleteIfExists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
    }

    @Test
    void generateCoverAsync_whenScreenshotEnabled_updatesCoverField() throws Exception {
        Path deployDir = Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        Files.createDirectories(deployDir);
        Files.writeString(deployDir.resolve("index.html"), "<html>cover</html>");

        doAnswer(invocation -> {
            Path outputPath = invocation.getArgument(1);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, new byte[] {1, 2, 3});
            return null;
        }).when(screenshotExecutor).capture(anyString(), eq(deployDir.resolve("cover.png")), anyInt(), anyInt());

        appCoverService.generateCoverAsync(appId, deployKey, "Test App", "Test Desc");

        for (int i = 0; i < 20; i++) {
            var app = appMapper.selectOneById(appId);
            if (app.getCover() != null) {
                assertThat(app.getCover()).isEqualTo("/api/deploy/" + deployKey + "/cover.png");
                assertThat(Files.exists(deployDir.resolve("cover.png"))).isTrue();
                return;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("cover 字段未在预期时间内更新");
    }

    private void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isRegularFile(path)) {
            Files.deleteIfExists(path);
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
