package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.UserInfoMapper;
import cn.wanyj.codefreex.model.dto.request.UserQueryRequest;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.wanyj.codefreex.model.entity.table.UserInfoTableDef.USER_INFO;

/**
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
    public UserInfo createUserInfo(Long userId, Long inviterId) {
        return createUserInfo(userId, inviterId, null, null);
    }

    @Override
    public UserInfo createUserInfo(Long userId, Long inviterId, String nickname, String avatar) {
        // 检查是否已存在
        UserInfo existing = getUserInfo(userId);
        if (existing != null) {
            return existing;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setInviterId(inviterId);
        userInfo.setNickname(nickname);
        userInfo.setAvatar(avatar);
        userInfo.setTotalCredits(0);
        userInfo.setRemainingCredits(0);
        userInfo.setStatus("active");
        userInfoMapper.insert(userInfo);
        return userInfo;
    }

    @Override
    public boolean updateUserInfo(Long userId, UserInfo updateInfo) {
        UserInfo existing = getUserInfo(userId);
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        updateInfo.setId(existing.getId());
        return userInfoMapper.update(updateInfo) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCredits(Long userId, int amount) {
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
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductCredits(Long userId, int amount) {
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
        return true;
    }

    @Override
    public void syncUserInfoFromRpc(Long userId, String nickname, String avatar) {
        UserInfo existing = getUserInfo(userId);
        if (existing == null) {
            // 本地无记录，创建
            createUserInfo(userId, null, nickname, avatar);
            return;
        }
        // 仅在有新值时更新
        boolean needUpdate = false;
        if (nickname != null && !nickname.equals(existing.getNickname())) {
            needUpdate = true;
        }
        if (avatar != null && !avatar.equals(existing.getAvatar())) {
            needUpdate = true;
        }
        if (needUpdate) {
            UpdateChain.of(UserInfo.class)
                    .where(USER_INFO.USER_ID.eq(userId))
                    .set(USER_INFO.NICKNAME, nickname, nickname != null)
                    .set(USER_INFO.AVATAR, avatar, avatar != null)
                    .update();
        }
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
    public PageResponse<UserInfo> listUsersForAdmin(UserQueryRequest request) {
        int pageSize = Math.min(request.getPageSize(), 50);

        QueryWrapper query = QueryWrapper.create();

        if (request.getSearchKey() != null && !request.getSearchKey().isEmpty()) {
            query.and(USER_INFO.NICKNAME.like(request.getSearchKey()));
        }
        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            query.and(USER_INFO.STATUS.eq(request.getStatus()));
        }

        query.orderBy(USER_INFO.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<UserInfo> page = userInfoMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(request.getPageNum(), pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public void setUserStatus(Long userId, String status) {
        if (!"active".equals(status) && !"disabled".equals(status)) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "状态只能为 active 或 disabled");
        }
        UserInfo existing = getUserInfo(userId);
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户不存在");
        }
        UpdateChain.of(UserInfo.class)
                .where(USER_INFO.USER_ID.eq(userId))
                .set(USER_INFO.STATUS, status)
                .update();
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        UserInfo existing = getUserInfo(userId);
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        UpdateChain.of(UserInfo.class)
                .where(USER_INFO.USER_ID.eq(userId))
                .set(USER_INFO.AVATAR, avatarUrl)
                .update();
    }

    @Override
    public void updateNickname(Long userId, String nickname) {
        UserInfo existing = getUserInfo(userId);
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        UpdateChain.of(UserInfo.class)
                .where(USER_INFO.USER_ID.eq(userId))
                .set(USER_INFO.NICKNAME, nickname)
                .update();
    }
}
