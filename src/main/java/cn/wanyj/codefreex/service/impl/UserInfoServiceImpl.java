package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.UserInfoMapper;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.UserInfoService;
import cn.wanyj.codefreex.service.policy.InviteCreditPolicy;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.wanyj.codefreex.model.entity.table.UserInfoTableDef.USER_INFO;

/**
 * 用户业务档案服务实现：码点/邀请等本地业务数据，身份信息不再落库
 *
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    @Lazy
    private final CreditTransactionService creditTransactionService;

    @Override
    public UserInfo getUserInfo(Long userId) {
        return userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfo createUserInfo(Long userId, Long inviterId) {
        // 检查是否已存在
        UserInfo existing = getUserInfo(userId);
        if (existing != null) {
            return existing;
        }
        int initialCredits = inviterId != null ? InviteCreditPolicy.INVITE_REWARD_CREDITS : 0;
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setInviterId(inviterId);
        userInfo.setTotalCredits(initialCredits);
        userInfo.setRemainingCredits(initialCredits);
        userInfoMapper.insert(userInfo);
        if (initialCredits > 0) {
            creditTransactionService.recordTransaction(
                    userId,
                    CreditTransactionType.GIFT,
                    initialCredits,
                    initialCredits,
                    CreditSourceType.REGISTER_GIFT,
                    inviterId,
                    "通过邀请码注册奖励",
                    inviterId
            );
        }
        return userInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addCredits(Long userId, int amount) {
        UserInfo userInfo = getUserInfo(userId);
        if (userInfo == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        int newTotal = userInfo.getTotalCredits() + amount;
        int newRemaining = userInfo.getRemainingCredits() + amount;
        UpdateChain.of(UserInfo.class)
                .where(USER_INFO.USER_ID.eq(userId))
                .set(USER_INFO.TOTAL_CREDITS, newTotal)
                .set(USER_INFO.REMAINING_CREDITS, newRemaining)
                .update();
        return newRemaining;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deductCredits(Long userId, int amount) {
        if (amount <= 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "扣减数量必须大于0");
        }
        UserInfo userInfo = getUserInfo(userId);
        if (userInfo == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        if (userInfo.getRemainingCredits() < amount) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "码点不足，请先兑换码点");
        }
        boolean updated = UpdateChain.of(UserInfo.class)
                .where(USER_INFO.USER_ID.eq(userId))
                .and(USER_INFO.REMAINING_CREDITS.ge(amount))
                .set(USER_INFO.REMAINING_CREDITS, userInfo.getRemainingCredits() - amount)
                .update();
        if (!updated) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "码点不足，请先兑换码点");
        }
        return userInfo.getRemainingCredits() - amount;
    }

    @Override
    public Map<Long, UserInfo> batchGetUserInfos(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<UserInfo> userInfos = userInfoMapper.selectListByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.in(userIds))
        );
        return userInfos.stream()
                .collect(Collectors.toMap(UserInfo::getUserId, u -> u, (a, b) -> a));
    }

    @Override
    public void deleteUserInfo(Long userId) {
        userInfoMapper.deleteByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId))
        );
    }
}
