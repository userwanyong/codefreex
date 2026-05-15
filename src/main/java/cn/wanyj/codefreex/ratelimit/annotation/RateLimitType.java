package cn.wanyj.codefreex.ratelimit.annotation;

/**
 * 限流类型
 *
 * @author wanyj
 */
public enum RateLimitType {

    /**
     * 用户级别（基于 userId）
     */
    USER,

    /**
     * IP 级别
     */
    IP
}
