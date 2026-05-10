package cn.wanyj.codefreex.service;

import java.nio.file.Path;

/**
 * 截图执行器
 *
 * @author BanXia
 */
public interface ScreenshotExecutor {

    void capture(String url, Path outputPath, int width, int height);
}
