package cn.wanyj.codefreex.ratelimit.aspect;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.ratelimit.RateLimitException;
import cn.wanyj.codefreex.ratelimit.annotation.RateLimit;
import cn.wanyj.codefreex.ratelimit.annotation.RateLimitType;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 分布式限流 AOP 切面（Redisson 令牌桶算法）
 *
 * @author wanyj
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final String KEY_PREFIX = AppConstant.REDIS_KEY_PREFIX + "ratelimit:";

    private final RedissonClient redissonClient;

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Around("@annotation(rateLimit)")
    public Object doRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String identifier = resolveIdentifier(rateLimit.limitType());
        String key = KEY_PREFIX + rateLimit.limitType().name().toLowerCase() + ":" + identifier;

        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // trySetRate 仅在 key 不存在时设置，避免覆盖已有配置
        limiter.trySetRate(RateType.OVERALL, rateLimit.rate(), rateLimit.rateInterval(), RateIntervalUnit.SECONDS);

        if (!limiter.tryAcquire(1)) {
            throw new RateLimitException(42900, rateLimit.message());
        }

        return joinPoint.proceed();
    }

    private String resolveIdentifier(RateLimitType limitType) {
        if (limitType == RateLimitType.USER) {
            Long userId = UserContext.getLoginUserId();
            if (userId != null) {
                return String.valueOf(userId);
            }
            // 未登录用户按 IP 限流
            return getClientIp();
        }
        return getClientIp();
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
