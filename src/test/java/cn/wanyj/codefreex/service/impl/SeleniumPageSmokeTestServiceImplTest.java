package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 页面冒烟检测：环境噪音过滤与缺文件短路（不含需真实浏览器的用例）
 *
 * @author wanyj
 */
class SeleniumPageSmokeTestServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void isEnvironmentNoise_filtersResourceAndCorsButKeepsRuntimeErrors() {
        assertThat(SeleniumPageSmokeTestServiceImpl.isEnvironmentNoise(
                "Failed to load resource: net::ERR_NAME_NOT_RESOLVED https://picsum.photos/800/600")).isTrue();
        assertThat(SeleniumPageSmokeTestServiceImpl.isEnvironmentNoise(
                "Access to script at 'file:///E:/app/main.js' from origin 'null' has been blocked by CORS policy")).isTrue();
        assertThat(SeleniumPageSmokeTestServiceImpl.isEnvironmentNoise(null)).isTrue();

        assertThat(SeleniumPageSmokeTestServiceImpl.isEnvironmentNoise(
                "Uncaught ReferenceError: renderChart is not defined")).isFalse();
        assertThat(SeleniumPageSmokeTestServiceImpl.isEnvironmentNoise(
                "Uncaught SyntaxError: Unexpected token '}'")).isFalse();
    }

    @Test
    void collectPageErrors_missingFile_returnsEmptyWithoutLaunchingBrowser() {
        SeleniumPageSmokeTestServiceImpl service = new SeleniumPageSmokeTestServiceImpl(
                null, new AppRuntimeConfig.ScreenshotProperties());

        List<String> errors = service.collectPageErrors(tempDir.resolve("not-exist").resolve("index.html"));

        assertThat(errors).isEmpty();
    }
}
