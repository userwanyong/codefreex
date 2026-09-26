package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.RequiredArgsConstructor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.springframework.stereotype.Component;

import java.util.logging.Level;

/**
 * 无头浏览器工厂：统一截图与页面冒烟检测的驱动创建逻辑。
 *
 * @author wanyj
 */
@Component
@RequiredArgsConstructor
public class HeadlessBrowserFactory {

    private final AppRuntimeConfig.ScreenshotProperties screenshotProperties;

    /**
     * 创建无头浏览器实例，调用方负责在 finally 中 quit()。
     * 始终启用浏览器控制台日志采集（goog:loggingPrefs），供页面冒烟检测读取 JS 运行错误。
     */
    public WebDriver create() {
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
            applyConsoleLogCapability(options);
            if (hasText(screenshotProperties.getBrowserBinaryPath())) {
                options.setBinary(screenshotProperties.getBrowserBinaryPath());
            }
            return new ChromeDriver(options);
        }

        // Edge（Chromium 内核，同样支持 goog:loggingPrefs）
        if (useLocalDriver) {
            System.setProperty("webdriver.edge.driver", screenshotProperties.getDriverPath());
        } else {
            WebDriverManager.edgedriver().setup();
        }
        EdgeOptions options = new EdgeOptions();
        options.addArguments("--headless=new", "--disable-gpu", "--no-sandbox");
        applyConsoleLogCapability(options);
        if (hasText(screenshotProperties.getBrowserBinaryPath())) {
            options.setBinary(screenshotProperties.getBrowserBinaryPath());
        }
        return new EdgeDriver(options);
    }

    private void applyConsoleLogCapability(MutableCapabilities options) {
        LoggingPreferences loggingPrefs = new LoggingPreferences();
        loggingPrefs.enable(LogType.BROWSER, Level.ALL);
        options.setCapability("goog:loggingPrefs", loggingPrefs);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
