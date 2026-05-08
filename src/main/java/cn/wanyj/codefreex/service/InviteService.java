package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.Invite;
import cn.wanyj.codefreex.model.entity.InviteUser;

import java.util.List;

/**
 * @author wanyj
 */
public interface InviteService {

    /**
     * 生成邀请码
     *
     * @param userId      生成者用户ID
     * @param batch       批次号（可选）
     * @param expireHours 过期时间（小时，null 表示永不过期）
     * @param maxUseCount 最大使用次数
     * @return 生成的邀请码
     */
    Invite generateInviteCode(Long userId, String batch, Integer expireHours, Integer maxUseCount);

    /**
     * 使用邀请码
     *
     * @param inviteCode 邀请码
     * @param inviteeId  受邀用户ID
     */
    void useInviteCode(String inviteCode, Long inviteeId);

    /**
     * 仅校验邀请码是否有效（不消费）
     *
     * @param inviteCode 邀请码
     * @return 邀请码对应的邀请人ID
     */
    Long validateInviteCode(String inviteCode);

    /**
     * 查询用户生成的邀请码列表
     */
    PageResponse<Invite> listInvites(Long userId, int pageNum, int pageSize);

    /**
     * 查询邀请人（谁邀请了我）
     */
    InviteUser getInviter(Long userId);

    /**
     * 查询某个邀请码的使用记录
     */
    List<InviteUser> listInviteUsers(Long inviteId);

    /**
     * 管理员分页查询所有邀请码
     */
    PageResponse<Invite> listAllInvites(int pageNum, int pageSize, String status);
}
