package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.service.ConcurrentTaskLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 并发任务限制器实现
 * 使用 Redisson 原子操作跟踪用户并发 AI 生成任务数
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrentTaskLimiterServiceImpl implements ConcurrentTaskLimiterService {

    private static final String KEY_PREFIX = AppConstant.REDIS_KEY_PREFIX + "concurrent:tasks:";
    private static final long KEY_TTL_SECONDS = 900;

    private final RedissonClient redissonClient;

    @Override
    public boolean isOverLimit(Long userId, int maxConcurrent) {
        RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + userId);
        return counter.isExists() && counter.get() >= maxConcurrent;
    }

    @Override
    public long tryAcquire(Long userId, int maxConcurrent) {
        RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + userId);
        long count = counter.incrementAndGet();

        // 每次都刷新 TTL，防止残留计数器过期后新任务 TTL 丢失
        counter.expire(KEY_TTL_SECONDS, TimeUnit.SECONDS);

        if (count > maxConcurrent) {
            // 超限回滚
            counter.decrementAndGet();
            log.warn("用户 {} 并发任务超限，当前={}, 最大={}", userId, count, maxConcurrent);
            return -1;
        }

        log.info("用户 {} 获取任务槽，当前并发={}", userId, count);
        return count;
    }

    @Override
    public void release(Long userId) {
        RAtomicLong counter = redissonClient.getAtomicLong(KEY_PREFIX + userId);
        long count = counter.decrementAndGet();

        if (count <= 0) {
            counter.delete();
            log.info("用户 {} 任务槽已清空", userId);
        } else {
            log.info("用户 {} 释放任务槽，剩余并发={}", userId, count);
        }
    }
}
