package cn.wanyj.codefreex.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Logback TurboFilter —— 抑制 Tomcat SSE 客户端断连时的 IOException ERROR 日志。
 *
 * SSE 连接在用户刷新/关闭页面时，StandardWrapperValve 会通过
 * org.apache.catalina.core.ContainerBase.[...].[dispatcherServlet] logger
 * 以 ERROR 级别记录 broken pipe IOException。
 * 这属于正常断连行为，不应作为错误告警，本 filter 将其降级为 DEBUG。
 * @author wanyj
 */
public class BrokenPipeTurboFilter extends TurboFilter {

    private static final String CATALINA_LOGGER_PREFIX = "org.apache.catalina.core.ContainerBase";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level,
                              String format, Object[] params, Throwable throwable) {
        // 只过滤 ERROR 级别
        if (!level.isGreaterOrEqual(Level.ERROR)) {
            return FilterReply.NEUTRAL;
        }
        // 只过滤 Tomcat ContainerBase logger
        if (logger == null || !logger.getName().startsWith(CATALINA_LOGGER_PREFIX)) {
            return FilterReply.NEUTRAL;
        }
        // 只过滤 IOException（broken pipe）
        if (throwable instanceof java.io.IOException) {
            return FilterReply.DENY;
        }
        // 检查消息中是否包含 broken pipe 关键词（有些日志 throwable 为 null 但消息中包含异常信息）
        if (format != null && (format.contains("中止了一个已建立的连接")
                || format.contains("Broken pipe")
                || format.contains("Connection reset")
                || format.contains("ClientAbortException"))) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
