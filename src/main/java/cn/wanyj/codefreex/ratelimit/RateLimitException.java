package cn.wanyj.codefreex.ratelimit;

import lombok.Getter;

/**
 * 限流异常，返回 HTTP 429
 *
 * @author wanyj
 */
@Getter
public class RateLimitException extends RuntimeException {

    private final int code;

    public RateLimitException(int code, String message) {
        super(message);
        this.code = code;
    }
}
