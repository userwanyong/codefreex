package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.mapper.CreditTransactionMapper;
import cn.wanyj.codefreex.model.entity.CreditTransaction;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.CreditTransactionService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.wanyj.codefreex.model.entity.table.CreditTransactionTableDef.CREDIT_TRANSACTION;

/**
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class CreditTransactionServiceImpl implements CreditTransactionService {

    private final CreditTransactionMapper creditTransactionMapper;

    @Override
    public CreditTransaction recordTransaction(Long userId, CreditTransactionType type, int amount,
                                                int balanceAfter, CreditSourceType sourceType,
                                                Long sourceId, String description, Long operatorId) {
        CreditTransaction transaction = new CreditTransaction();
        transaction.setUserId(userId);
        transaction.setType(type.getValue());
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setSourceType(sourceType != null ? sourceType.getValue() : null);
        transaction.setSourceId(sourceId);
        transaction.setDescription(description);
        transaction.setOperatorId(operatorId);
        creditTransactionMapper.insert(transaction);
        return transaction;
    }

    @Override
    public PageResponse<CreditTransaction> listTransactions(Long userId, int pageNum, int pageSize) {
        pageSize = Math.min(pageSize, 50);
        QueryWrapper query = QueryWrapper.create()
                .where(CREDIT_TRANSACTION.USER_ID.eq(userId))
                .orderBy(CREDIT_TRANSACTION.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<CreditTransaction> page = creditTransactionMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public PageResponse<CreditTransaction> listAllTransactions(int pageNum, int pageSize, Long userId, String type) {
        pageSize = Math.min(pageSize, 50);
        QueryWrapper query = QueryWrapper.create();

        if (userId != null) {
            query.and(CREDIT_TRANSACTION.USER_ID.eq(userId));
        }
        if (type != null && !type.isEmpty()) {
            query.and(CREDIT_TRANSACTION.TYPE.eq(type));
        }
        query.orderBy(CREDIT_TRANSACTION.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<CreditTransaction> page = creditTransactionMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }
}
