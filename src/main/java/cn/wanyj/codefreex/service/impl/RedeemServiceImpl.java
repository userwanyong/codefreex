package cn.wanyj.codefreex.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.RedeemMapper;
import cn.wanyj.codefreex.mapper.RedeemUserMapper;
import cn.wanyj.codefreex.model.entity.Redeem;
import cn.wanyj.codefreex.model.entity.RedeemUser;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.model.enums.InviteStatus;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.RedeemService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.wanyj.codefreex.model.entity.table.RedeemTableDef.REDEEM;
/**
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class RedeemServiceImpl implements RedeemService {

    private final RedeemMapper redeemMapper;
    private final RedeemUserMapper redeemUserMapper;
    private final UserInfoService userInfoService;
    private final CreditTransactionService creditTransactionService;

    @Override
    public Redeem generateRedeemCode(Long adminId, int quota, String batch, Integer expireHours, Integer maxUseCount) {
        Redeem redeem = new Redeem();
        redeem.setRedeemCode(generateCode("RDM"));
        redeem.setUserId(adminId);
        redeem.setBatch(batch);
        redeem.setQuota(quota);
        redeem.setStatus(InviteStatus.UNUSED.getValue());
        redeem.setExpireTime(expireHours != null ? LocalDateTime.now().plusHours(expireHours) : null);
        redeem.setMaxUseCount(maxUseCount != null ? maxUseCount : 1);
        redeem.setUsedCount(0);
        redeemMapper.insert(redeem);
        return redeem;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useRedeemCode(String redeemCode, Long userId) {
        // 1. 查询兑换码
        Redeem redeem = redeemMapper.selectOneByQuery(
                QueryWrapper.create().where(REDEEM.REDEEM_CODE.eq(redeemCode))
        );
        if (redeem == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "兑换码不存在");
        }

        // 2. 校验状态
        InviteStatus status = InviteStatus.fromValue(redeem.getStatus());
        if (status == InviteStatus.DISABLED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "兑换码已被禁用");
        }
        if (status == InviteStatus.USED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "兑换码已用完");
        }

        // 3. 校验过期
        if (redeem.getExpireTime() != null && redeem.getExpireTime().isBefore(LocalDateTime.now())) {
            UpdateChain.of(Redeem.class)
                    .where(REDEEM.ID.eq(redeem.getId()))
                    .set(REDEEM.STATUS, InviteStatus.EXPIRED.getValue())
                    .update();
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "兑换码已过期");
        }

        // 4. 校验次数
        if (redeem.getUsedCount() >= redeem.getMaxUseCount()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "兑换码已用完");
        }

        // 5. 更新使用次数和状态
        int newUsedCount = redeem.getUsedCount() + 1;
        String newStatus = newUsedCount >= redeem.getMaxUseCount()
                ? InviteStatus.USED.getValue()
                : InviteStatus.PARTIAL.getValue();

        UpdateChain.of(Redeem.class)
                .where(REDEEM.ID.eq(redeem.getId()))
                .set(REDEEM.USED_COUNT, newUsedCount)
                .set(REDEEM.STATUS, newStatus)
                .update();

        // 6. 增加用户码点
        userInfoService.addCredits(userId, redeem.getQuota());

        // 7. 记录码点流水
        UserInfo updatedUserInfo = userInfoService.getUserInfo(userId);
        creditTransactionService.recordTransaction(
                userId,
                CreditTransactionType.RECHARGE,
                redeem.getQuota(),
                updatedUserInfo.getRemainingCredits(),
                CreditSourceType.REDEEM,
                redeem.getId(),
                "兑换码充值: " + redeemCode,
                null
        );

        // 8. 创建兑换-用户关联记录
        RedeemUser redeemUser = new RedeemUser();
        redeemUser.setRedeemId(redeem.getId());
        redeemUser.setCreatorId(redeem.getUserId());
        redeemUser.setUserId(userId);
        redeemUserMapper.insert(redeemUser);
    }

    @Override
    public PageResponse<Redeem> listRedeems(int pageNum, int pageSize, String status) {
        QueryWrapper query = QueryWrapper.create();
        if (status != null && !status.isEmpty()) {
            query.where(REDEEM.STATUS.eq(status));
        }
        query.orderBy(REDEEM.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<Redeem> page = redeemMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(), (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public Redeem getRedeemDetail(Long redeemId) {
        return redeemMapper.selectOneById(redeemId);
    }

    @Override
    public List<RedeemUser> listRedeemUsers(Long redeemId) {
        return redeemUserMapper.selectListByQuery(
                QueryWrapper.create().where(cn.wanyj.codefreex.model.entity.table.RedeemUserTableDef.REDEEM_USER.REDEEM_ID.eq(redeemId))
                        .orderBy(cn.wanyj.codefreex.model.entity.table.RedeemUserTableDef.REDEEM_USER.CREATE_TIME.desc())
        );
    }

    private String generateCode(String prefix) {
        return prefix + "_" + IdUtil.getSnowflakeNextIdStr();
    }
}
