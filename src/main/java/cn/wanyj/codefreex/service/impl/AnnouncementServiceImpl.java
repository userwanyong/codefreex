package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AnnouncementAckMapper;
import cn.wanyj.codefreex.mapper.AnnouncementMapper;
import cn.wanyj.codefreex.model.dto.request.AnnouncementCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AnnouncementUpdateRequest;
import cn.wanyj.codefreex.model.entity.Announcement;
import cn.wanyj.codefreex.model.entity.AnnouncementAck;
import cn.wanyj.codefreex.service.AnnouncementService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.wanyj.codefreex.model.entity.table.AnnouncementAckTableDef.ANNOUNCEMENT_ACK;
import static cn.wanyj.codefreex.model.entity.table.AnnouncementTableDef.ANNOUNCEMENT;

/**
 * 公告服务实现
 *
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_OFFLINE = "offline";

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementAckMapper announcementAckMapper;

    @Override
    public Announcement getActiveAnnouncement(Long userId) {
        Announcement latest = announcementMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(ANNOUNCEMENT.STATUS.eq(STATUS_PUBLISHED))
                        .and(ANNOUNCEMENT.IS_DELETE.eq(0))
                        .orderBy(ANNOUNCEMENT.PUBLISH_TIME.desc(), ANNOUNCEMENT.ID.desc())
                        .limit(1)
        );
        if (latest == null) {
            return null;
        }
        if (userId != null && hasAcked(userId, latest.getId())) {
            return null;
        }
        return latest;
    }

    @Override
    public void ackAnnouncement(Long userId, Long announcementId) {
        Announcement announcement = announcementMapper.selectOneById(announcementId);
        if (announcement == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "公告不存在");
        }
        if (hasAcked(userId, announcementId)) {
            return;
        }
        AnnouncementAck ack = new AnnouncementAck();
        ack.setAnnouncementId(announcementId);
        ack.setUserId(userId);
        ack.setCreateTime(LocalDateTime.now());
        announcementAckMapper.insert(ack);
    }

    @Override
    public PageResponse<Announcement> adminListAnnouncements(int pageNum, int pageSize) {
        pageSize = Math.min(pageSize, 50);
        QueryWrapper query = QueryWrapper.create()
                .where(ANNOUNCEMENT.IS_DELETE.eq(0))
                .orderBy(ANNOUNCEMENT.CREATE_TIME.desc());
        Page<Announcement> page = announcementMapper.paginate(new Page<>(pageNum, pageSize), query);
        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Announcement adminCreateAnnouncement(AnnouncementCreateRequest request) {
        String status = normalizeStatus(request.getStatus(), STATUS_DRAFT);
        Announcement announcement = new Announcement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setStatus(status);
        announcement.setPublishTime(STATUS_PUBLISHED.equals(status) ? LocalDateTime.now() : null);
        announcement.setIsDelete(0);
        announcementMapper.insert(announcement);
        return announcement;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateAnnouncement(AnnouncementUpdateRequest request) {
        Announcement existing = announcementMapper.selectOneById(request.getId());
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "公告不存在");
        }
        String status = normalizeStatus(request.getStatus(), existing.getStatus());
        boolean republish = STATUS_PUBLISHED.equals(status)
                && (isChanged(existing.getTitle(), request.getTitle())
                || isChanged(existing.getContent(), request.getContent()));
        // 已发布内容被修改视为新公告：刷新发布时间并重置用户确认，确保用户能看到更新；
        // 非发布态（草稿/下线）发布时间置空
        LocalDateTime publishTime;
        if (!STATUS_PUBLISHED.equals(status)) {
            publishTime = null;
        } else if (republish || existing.getPublishTime() == null) {
            publishTime = LocalDateTime.now();
        } else {
            publishTime = existing.getPublishTime();
        }
        UpdateChain.of(Announcement.class)
                .where(ANNOUNCEMENT.ID.eq(request.getId()))
                .set(ANNOUNCEMENT.TITLE, request.getTitle())
                .set(ANNOUNCEMENT.CONTENT, request.getContent())
                .set(ANNOUNCEMENT.STATUS, status)
                .set(ANNOUNCEMENT.PUBLISH_TIME, publishTime)
                .update();
        if (republish) {
            announcementAckMapper.deleteByQuery(
                    QueryWrapper.create().where(ANNOUNCEMENT_ACK.ANNOUNCEMENT_ID.eq(request.getId()))
            );
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteAnnouncement(Long announcementId) {
        Announcement existing = announcementMapper.selectOneById(announcementId);
        if (existing == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "公告不存在");
        }
        UpdateChain.of(Announcement.class)
                .where(ANNOUNCEMENT.ID.eq(announcementId))
                .set(ANNOUNCEMENT.IS_DELETE, 1)
                .update();
        announcementAckMapper.deleteByQuery(
                QueryWrapper.create().where(ANNOUNCEMENT_ACK.ANNOUNCEMENT_ID.eq(announcementId))
        );
    }

    private boolean hasAcked(Long userId, Long announcementId) {
        return announcementAckMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(ANNOUNCEMENT_ACK.ANNOUNCEMENT_ID.eq(announcementId))
                        .and(ANNOUNCEMENT_ACK.USER_ID.eq(userId))
        ) > 0;
    }

    private String normalizeStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        if (STATUS_DRAFT.equals(status) || STATUS_PUBLISHED.equals(status) || STATUS_OFFLINE.equals(status)) {
            return status;
        }
        throw new BusinessException(ResponseCode.PARAMS_ERROR, "非法的公告状态: " + status);
    }

    private boolean isChanged(String oldValue, String newValue) {
        return !Objects.equals(oldValue, newValue);
    }
}
