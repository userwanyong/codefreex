package cn.wanyj.codefreex.ratelimit.annotation;

import java.lang.annotation.*;

/**
 * 分布式限流注解（基于 Redisson 令牌桶算法）
 * <p>
 * 标注在 Controller 方法上，支持用户级别和 IP 级别限流
 *
 * @author wanyj
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流类型：USER（用户级别）或 IP（IP 级别）
     */
    RateLimitType limitType() default RateLimitType.USER;

    /**
     * 时间窗口内允许的请求数
     */
    long rate() default 5;

    /**
     * 时间窗口（秒）
     */
    long rateInterval() default 60;

    /**
     * 超限提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}
