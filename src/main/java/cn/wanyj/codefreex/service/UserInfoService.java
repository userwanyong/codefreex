package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.entity.UserInfo;

import java.util.Map;
import java.util.Set;

/**
 * 用户业务档案服务：码点与邀请关系（用户身份信息由 auth-service 统一管理）
 *
 * @author wanyj
 */
public interface UserInfoService {

    /**
     * 根据用户ID获取用户业务档案
     */
    UserInfo getUserInfo(Long userId);

    /**
     * 用户首次进入业务系统时创建档案（存在则直接返回）
     */
    UserInfo createUserInfo(Long userId, Long inviterId);

    /**
     * 增加用户码点
     *
     * @return 增加后的剩余码点
     */
    int addCredits(Long userId, int amount);

    /**
     * 扣减用户码点（余额不足抛异常）
     *
     * @return 扣减后的剩余码点
     */
    int deductCredits(Long userId, int amount);

    /**
     * 批量查询用户业务档案
     */
    Map<Long, UserInfo> batchGetUserInfos(Set<Long> userIds);

    /**
     * 删除用户业务档案（用户被删除时清理本地数据）
     */
    void deleteUserInfo(Long userId);
}
