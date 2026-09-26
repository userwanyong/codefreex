package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.service.PageSmokeTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;

/**
 * 基于 Selenium 无头浏览器的页面冒烟检测实现。
 *
 * 设计约束：
 * - 冒烟是增强校验，任何检测环境问题（浏览器/驱动缺失、超时）都按通过处理，
 *   只记日志不阻断主流程；
 * - 过滤环境噪音（资源加载失败、file:// 下的 CORS），只上报真正的 JS 运行错误，
 *   避免修复循环被非代码缺陷问题空转；
 * - file:// 只适用于经典脚本的产物（HTML 单文件 / 多文件），Vue 产物是 ES module
 *   会因 file:// CORS 全部加载失败，不适用本检测（由构建校验兜底）。
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeleniumPageSmokeTestServiceImpl implements PageSmokeTestService {

    /** 单次冒烟最多上报的错误条数，避免修复提示词被长日志撑爆 */
    private static final int MAX_REPORT_ERRORS = 8;

    private final HeadlessBrowserFactory browserFactory;
    private final AppRuntimeConfig.ScreenshotProperties screenshotProperties;

    @Override
    public List<String> collectPageErrors(Path indexHtml) {
        if (!Files.exists(indexHtml)) {
            return List.of();
        }
        WebDriver driver = null;
        try {
            driver = browserFactory.create();
            driver.manage().timeouts().pageLoadTimeout(
                    Duration.ofSeconds(screenshotProperties.getPageLoadTimeoutSeconds()));
            driver.get(indexHtml.toUri().toString());
            // 等待渲染稳定，让同步脚本与常见异步初始化跑完
            Thread.sleep(screenshotProperties.getRenderWaitMillis());
            List<String> errors = driver.manage().logs().get(LogType.BROWSER).getAll().stream()
                    .filter(entry -> entry.getLevel() == Level.SEVERE)
                    .map(LogEntry::getMessage)
                    .filter(message -> !isEnvironmentNoise(message))
                    .limit(MAX_REPORT_ERRORS)
                    .toList();
            if (!errors.isEmpty()) {
                log.warn("[PageSmoke] 检测到 {} 处页面运行错误: {}", errors.size(), indexHtml);
            }
            return errors;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[PageSmoke] 冒烟检测被中断(按通过处理): {}", e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.warn("[PageSmoke] 冒烟检测执行失败(按通过处理): {}, 原因: {}", indexHtml, e.getMessage());
            return List.of();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 环境噪音判定：与本地沙箱/网络环境相关、不代表生成代码缺陷的报错。
     * - Failed to load resource / net::ERR_*：外链图片、CDN 资源在服务器网络下加载失败；
     * - CORS policy：file:// 加载 ES module 的必然报错（HTTP 部署下不存在）。
     */
    static boolean isEnvironmentNoise(String message) {
        if (message == null) {
            return true;
        }
        return message.contains("Failed to load resource")
                || message.contains("net::ERR_")
                || message.contains("CORS policy");
    }
}
