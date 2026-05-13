package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.mapper.UserUsageMapper;
import cn.wanyj.codefreex.model.entity.UserUsage;
import cn.wanyj.codefreex.service.UserUsageService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.wanyj.codefreex.model.entity.table.UserUsageTableDef.USER_USAGE;

/**
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class UserUsageServiceImpl implements UserUsageService {

    private final UserUsageMapper userUsageMapper;

    @Override
    public UserUsage recordUsage(Long userId, Long appId, String modelId,
                                  int inputTokens, int outputTokens, int totalTokens,
                                  int latency, String status, String errorInfo) {
        UserUsage usage = new UserUsage();
        usage.setUserId(userId);
        usage.setAppId(appId);
        usage.setModelId(modelId);
        usage.setInputTokens(inputTokens);
        usage.setOutputTokens(outputTokens);
        usage.setTotalTokens(totalTokens);
        usage.setLatency(latency);
        usage.setStatus(status);
        usage.setErrorInfo(errorInfo);
        userUsageMapper.insert(usage);
        return usage;
    }

    @Override
    public PageResponse<UserUsage> listUsagesForAdmin(int pageNum, int pageSize, Long userId, Long appId) {
        pageSize = Math.min(pageSize, 50);
        QueryWrapper query = QueryWrapper.create();

        if (userId != null) {
            query.and(USER_USAGE.USER_ID.eq(userId));
        }
        if (appId != null) {
            query.and(USER_USAGE.APP_ID.eq(appId));
        }
        query.orderBy(USER_USAGE.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<UserUsage> page = userUsageMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }
}
