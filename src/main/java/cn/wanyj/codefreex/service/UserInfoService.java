package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.entity.UserInfo;

/**
 * @author wanyj
 */
public interface UserInfoService {

    /**
     * 根据用户ID获取用户关联信息
     */
    UserInfo getUserInfo(Long userId);

    /**
     * 注册时创建用户关联记录
     */
    UserInfo createUserInfo(Long userId, Long inviterId);

    /**
     * 修改用户信息
     */
    boolean updateUserInfo(Long userId, UserInfo updateInfo);

    /**
     * 增加用户额度
     */
    boolean addCredits(Long userId, int amount);
}
