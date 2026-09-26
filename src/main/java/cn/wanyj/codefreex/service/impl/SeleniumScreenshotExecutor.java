package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.ScreenshotExecutor;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Selenium 截图执行器
 *
 * @author BanXia
 */
@Service
@RequiredArgsConstructor
public class SeleniumScreenshotExecutor implements ScreenshotExecutor {

    private final AppRuntimeConfig.ScreenshotProperties screenshotProperties;
    private final HeadlessBrowserFactory browserFactory;

    @Override
    public void capture(String url, Path outputPath, int width, int height) {
        WebDriver webDriver = browserFactory.create();
        try {
            webDriver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(screenshotProperties.getPageLoadTimeoutSeconds()));
            webDriver.manage().window().setSize(new Dimension(width, height));
            webDriver.get(url);
            Thread.sleep(screenshotProperties.getRenderWaitMillis());
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("截图被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("Selenium 截图失败: " + e.getMessage(), e);
        } finally {
            webDriver.quit();
        }
    }
}
