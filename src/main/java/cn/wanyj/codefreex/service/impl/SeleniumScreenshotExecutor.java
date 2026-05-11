package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.ScreenshotExecutor;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
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

    @Override
    public void capture(String url, Path outputPath, int width, int height) {
        WebDriver webDriver = createDriver();
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

    private WebDriver createDriver() {
        String browser = screenshotProperties.getBrowser();
        boolean useLocalDriver = hasText(screenshotProperties.getDriverPath());

        if ("chrome".equalsIgnoreCase(browser)) {
            if (useLocalDriver) {
                System.setProperty("webdriver.chrome.driver", screenshotProperties.getDriverPath());
            } else {
                WebDriverManager.chromedriver().setup();
            }
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox");
            if (hasText(screenshotProperties.getBrowserBinaryPath())) {
                options.setBinary(screenshotProperties.getBrowserBinaryPath());
            }
            return new ChromeDriver(options);
        }

        // Edge
        if (useLocalDriver) {
            System.setProperty("webdriver.edge.driver", screenshotProperties.getDriverPath());
        } else {
            WebDriverManager.edgedriver().setup();
        }
        EdgeOptions options = new EdgeOptions();
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox");
        if (hasText(screenshotProperties.getBrowserBinaryPath())) {
            options.setBinary(screenshotProperties.getBrowserBinaryPath());
        }
        return new EdgeDriver(options);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
