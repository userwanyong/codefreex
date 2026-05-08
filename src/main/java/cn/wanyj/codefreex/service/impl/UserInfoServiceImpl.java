package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.UserInfoMapper;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.wanyj.codefreex.model.entity.table.UserInfoTableDef.USER_INFO;
/**
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;

    @Override
    public UserInfo getUserInfo(Long userId) {
        return userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId))
        );
    }

    @Override
    public UserInfo createUserInfo(Long userId, Long inviterId) {
        // 检查是否已存在
        UserInfo existing = getUserInfo(userId);
        if (existing != null) {
            return existing;
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setInviterId(inviterId);
        userInfo.setTotalCredits(0);
        userInfo.setRemainingCredits(0);
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
    public boolean addCredits(Long userId, int amount) {
        UserInfo userInfo = getUserInfo(userId);
        if (userInfo == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "用户信息不存在");
        }
        userInfo.setTotalCredits(userInfo.getTotalCredits() + amount);
        userInfo.setRemainingCredits(userInfo.getRemainingCredits() + amount);
        return userInfoMapper.update(userInfo) > 0;
    }
}
