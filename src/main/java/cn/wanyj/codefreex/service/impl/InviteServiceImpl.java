package cn.wanyj.codefreex.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.InviteMapper;
import cn.wanyj.codefreex.mapper.InviteUserMapper;
import cn.wanyj.codefreex.model.entity.Invite;
import cn.wanyj.codefreex.model.entity.InviteUser;
import cn.wanyj.codefreex.model.enums.InviteStatus;
import cn.wanyj.codefreex.service.InviteService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static cn.wanyj.codefreex.model.entity.table.InviteTableDef.INVITE;
import static cn.wanyj.codefreex.model.entity.table.InviteUserTableDef.INVITE_USER;

/**
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    private final InviteMapper inviteMapper;
    private final InviteUserMapper inviteUserMapper;

    @Override
    public Invite generateInviteCode(Long userId, String batch, Integer expireHours, Integer maxUseCount) {
        Invite invite = new Invite();
        invite.setInviteCode(generateCode("INV"));
        invite.setUserId(userId);
        invite.setBatch(batch);
        invite.setStatus(InviteStatus.UNUSED.getValue());
        invite.setExpireTime(expireHours != null ? LocalDateTime.now().plusHours(expireHours) : null);
        invite.setMaxUseCount(maxUseCount != null ? maxUseCount : 1);
        invite.setUsedCount(0);
        inviteMapper.insert(invite);
        return invite;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useInviteCode(String inviteCode, Long inviteeId) {
        // 1. 查询邀请码
        Invite invite = inviteMapper.selectOneByQuery(
                QueryWrapper.create().where(INVITE.INVITE_CODE.eq(inviteCode))
        );
        if (invite == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码不存在");
        }

        // 2. 校验状态
        InviteStatus status = InviteStatus.fromValue(invite.getStatus());
        if (status == InviteStatus.DISABLED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已被禁用");
        }
        if (status == InviteStatus.USED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已用完");
        }

        // 3. 校验过期
        if (invite.getExpireTime() != null && invite.getExpireTime().isBefore(LocalDateTime.now())) {
            UpdateChain.of(Invite.class)
                    .where(INVITE.ID.eq(invite.getId()))
                    .set(INVITE.STATUS, InviteStatus.EXPIRED.getValue())
                    .update();
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已过期");
        }

        // 4. 校验次数
        if (invite.getUsedCount() >= invite.getMaxUseCount()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已用完");
        }

        // 5. 更新使用次数和状态
        int newUsedCount = invite.getUsedCount() + 1;
        String newStatus = newUsedCount >= invite.getMaxUseCount()
                ? InviteStatus.USED.getValue()
                : InviteStatus.PARTIAL.getValue();

        UpdateChain.of(Invite.class)
                .where(INVITE.ID.eq(invite.getId()))
                .set(INVITE.USED_COUNT, newUsedCount)
                .set(INVITE.STATUS, newStatus)
                .update();

        // 6. 创建邀请-用户关联记录
        InviteUser inviteUser = new InviteUser();
        inviteUser.setInviteId(invite.getId());
        inviteUser.setInviterId(invite.getUserId());
        inviteUser.setInviteeId(inviteeId);
        inviteUserMapper.insert(inviteUser);
    }

    @Override
    public Long validateInviteCode(String inviteCode) {
        Invite invite = inviteMapper.selectOneByQuery(
                QueryWrapper.create().where(INVITE.INVITE_CODE.eq(inviteCode))
        );
        if (invite == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码不存在");
        }

        InviteStatus status = InviteStatus.fromValue(invite.getStatus());
        if (status == InviteStatus.DISABLED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已被禁用");
        }
        if (status == InviteStatus.USED) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已用完");
        }

        if (invite.getExpireTime() != null && invite.getExpireTime().isBefore(LocalDateTime.now())) {
            UpdateChain.of(Invite.class)
                    .where(INVITE.ID.eq(invite.getId()))
                    .set(INVITE.STATUS, InviteStatus.EXPIRED.getValue())
                    .update();
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已过期");
        }

        if (invite.getUsedCount() >= invite.getMaxUseCount()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "邀请码已用完");
        }

        return invite.getUserId();
    }

    @Override
    public PageResponse<Invite> listInvites(Long userId, int pageNum, int pageSize) {
        QueryWrapper query = QueryWrapper.create()
                .where(INVITE.USER_ID.eq(userId))
                .orderBy(INVITE.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<Invite> page = inviteMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(), (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public InviteUser getInviter(Long userId) {
        return inviteUserMapper.selectOneByQuery(
                QueryWrapper.create().where(INVITE_USER.INVITEE_ID.eq(userId))
        );
    }

    @Override
    public List<InviteUser> listInviteUsers(Long inviteId) {
        return inviteUserMapper.selectListByQuery(
                QueryWrapper.create().where(INVITE_USER.INVITE_ID.eq(inviteId))
                        .orderBy(INVITE_USER.CREATE_TIME.desc())
        );
    }

    @Override
    public PageResponse<Invite> listAllInvites(int pageNum, int pageSize, String status) {
        QueryWrapper query = QueryWrapper.create();
        if (status != null && !status.isEmpty()) {
            query.where(INVITE.STATUS.eq(status));
        }
        query.orderBy(INVITE.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<Invite> page = inviteMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(), (int) page.getPageNumber(), (int) page.getPageSize());
    }

    private String generateCode(String prefix) {
        return prefix + "_" + IdUtil.getSnowflakeNextIdStr();
    }
}
