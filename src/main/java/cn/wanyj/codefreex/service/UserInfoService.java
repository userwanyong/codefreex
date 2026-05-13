package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.dto.request.UserQueryRequest;
import cn.wanyj.codefreex.model.entity.UserInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 注册时创建用户关联记录（含昵称头像）
     */
    UserInfo createUserInfo(Long userId, Long inviterId, String nickname, String avatar);

    /**
     * 修改用户信息
     */
    boolean updateUserInfo(Long userId, UserInfo updateInfo);

    /**
     * 增加用户码点
     */
    boolean addCredits(Long userId, int amount);

    /**
     * 扣减用户码点（余额不足抛异常）
     */
    boolean deductCredits(Long userId, int amount);

    /**
     * 从RPC同步用户昵称头像到本地
     */
    void syncUserInfoFromRpc(Long userId, String nickname, String avatar);

    /**
     * 批量查询用户信息
     */
    Map<Long, UserInfo> batchGetUserInfos(Set<Long> userIds);

    /**
     * 管理员分页查询用户列表
     */
    PageResponse<UserInfo> listUsersForAdmin(UserQueryRequest request);

    /**
     * 设置用户状态（启用/禁用）
     */
    void setUserStatus(Long userId, String status);
}
