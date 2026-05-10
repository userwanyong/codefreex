package cn.wanyj.codefreex.service.impl.integration;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.service.AppCoverService;
import cn.wanyj.codefreex.service.AppDeployService;
import cn.wanyj.codefreex.testutil.IntegrationTestConfig;
import com.mybatisflex.core.update.UpdateChain;
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
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author BanXia
 */
@SpringBootTest(classes = cn.wanyj.codefreex.TestApplication.class)
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class AppDeployServiceIntegrationTest {

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private AppCoverService appCoverService;

    @Autowired
    private AppDeployService appDeployService;

    @Autowired
    private AppMapper appMapper;

    private final Long appId = 1001L;
    private final Long userId = 2001L;
    private final String deployKey = "DK_TEST_001";

    @AfterEach
    void tearDown() throws IOException {
        deleteIfExists(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey));
        deleteIfExists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
        deleteIfExists(Path.of(AppConstant.CODE_DOWNLOAD_ROOT_DIR, deployKey + ".zip"));
        deleteIfExists(Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d", deployKey + ".conf"));
    }

    @Test
    void deployApp_generatedApp_copiesFilesAndUpdatesStatus() throws Exception {
        Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), "<html>deploy me</html>");

        updateAppStatus("generated");

        var response = appDeployService.deployApp(userId, appId);
        App app = appMapper.selectOneById(appId);

        assertThat(response.getStatus()).isEqualTo("deployed");
        assertThat(app.getStatus()).isEqualTo("deployed");
        assertThat(app.getDeployedTime()).isNotNull();
        assertThat(Files.readString(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey, "index.html")))
                .contains("deploy me");
        assertThat(Files.exists(Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d", deployKey + ".conf"))).isTrue();
    }

    @Test
    void cancelDeploy_removesDeployedFilesAndResetsStatus() throws Exception {
        Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), "<html>deploy me</html>");
        updateAppStatus("generated");
        appDeployService.deployApp(userId, appId);

        appDeployService.cancelDeploy(userId, appId);
        App app = appMapper.selectOneById(appId);

        assertThat(app.getStatus()).isEqualTo("generated");
        assertThat(app.getDeployedTime()).isNull();
        assertThat(Files.exists(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey))).isFalse();
        assertThat(Files.exists(Path.of(AppConstant.CODE_NGINX_ROOT_DIR, "conf.d", deployKey + ".conf"))).isFalse();
    }

    @Test
    void prepareDownloadArchive_returnsUsableZip() throws Exception {
        Path outputDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("index.html"), "<html>download</html>");

        AppDeployService.DownloadArchive archive = appDeployService.prepareDownloadArchive(userId, appId);

        assertThat(Files.exists(archive.archivePath())).isTrue();
        try (ZipFile zipFile = new ZipFile(archive.archivePath().toFile())) {
            assertThat(zipFile.getEntry("index.html")).isNotNull();
        }

        Path unzipDir = Files.createTempDirectory("p6-download-");
        try {
            unzip(archive.archivePath(), unzipDir);
            assertThat(Files.readString(unzipDir.resolve("index.html"))).contains("download");
        } finally {
            deleteIfExists(unzipDir);
        }
    }

    private void updateAppStatus(String status) {
        UpdateChain.of(cn.wanyj.codefreex.model.entity.App.class)
                .where(APP.ID.eq(appId))
                .set(APP.STATUS, status)
                .update();
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

    private void unzip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName()).normalize();
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(zipInputStream, outputPath);
                }
                zipInputStream.closeEntry();
            }
        }
    }
}
