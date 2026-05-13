package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.UserUsage;

/**
 * 用户用量统计服务
 *
 * @author wanyj
 */
public interface UserUsageService {

    /**
     * 记录用户用量
     */
    UserUsage recordUsage(Long userId, Long appId, String modelId,
                          int inputTokens, int outputTokens, int totalTokens,
                          int latency, String status, String errorInfo);

    /**
     * 管理员分页查询用量
     */
    PageResponse<UserUsage> listUsagesForAdmin(int pageNum, int pageSize, Long userId, Long appId);
}
