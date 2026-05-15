package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.CreditTransaction;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.model.enums.CreditSourceType;

/**
 * 码点流水服务
 *
 * @author wanyj
 */
public interface CreditTransactionService {

    /**
     * 记录码点流水
     */
    CreditTransaction recordTransaction(Long userId, CreditTransactionType type, int amount,
                                        int balanceAfter, CreditSourceType sourceType,
                                        Long sourceId, String description, Long operatorId);

    /**
     * 分页查询用户码点流水
     */
    PageResponse<CreditTransaction> listTransactions(Long userId, int pageNum, int pageSize);

    /**
     * 管理员分页查询码点流水
     */
    PageResponse<CreditTransaction> listAllTransactions(int pageNum, int pageSize, Long userId, String type);
}
