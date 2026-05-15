package cn.wanyj.codefreex.service;

/**
 * 并发任务限制器
 * 基于 Redis 原子操作跟踪用户并发任务数
 *
 * @author wanyj
 */
public interface ConcurrentTaskLimiterService {

    /**
     * 检查是否超过并发限制（只读，不增加计数）
     *
     * @param userId        用户 ID
     * @param maxConcurrent 最大并发数
     * @return true = 已超限
     */
    boolean isOverLimit(Long userId, int maxConcurrent);

    /**
     * 尝试获取任务槽
     *
     * @param userId        用户 ID
     * @param maxConcurrent 最大并发数
     * @return 当前并发数；超限返回 -1
     */
    long tryAcquire(Long userId, int maxConcurrent);

    /**
     * 释放任务槽
     *
     * @param userId 用户 ID
     */
    void release(Long userId);
}
