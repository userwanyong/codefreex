package cn.wanyj.codefreex.service;

import java.nio.file.Path;
import java.util.List;

/**
 * 页面冒烟检测：用无头浏览器加载生成产物，捕获 JS 运行时错误。
 * 作为 HTML / 多文件产物质检的增强校验——这两类产物没有构建环节，
 * 语法错误、未定义变量等运行时问题此前会静默通过质检直接交付。
 *
 * @author wanyj
 */
public interface PageSmokeTestService {

    /**
     * 加载指定 index.html 并收集浏览器控制台中的严重错误。
     *
     * @return 错误消息列表；空列表表示通过、无产物可测或检测环境不可用（跳过，不阻断主流程）
     */
    List<String> collectPageErrors(Path indexHtml);
}
