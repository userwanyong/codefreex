package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.dto.request.AnnouncementCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AnnouncementUpdateRequest;
import cn.wanyj.codefreex.model.entity.Announcement;

/**
 * 公告服务
 *
 * @author wanyj
 */
public interface AnnouncementService {

    /**
     * 查询当前生效公告（最新发布的一条）；已登录用户已确认“不再弹出”则返回 null
     *
     * @param userId 当前用户 id，匿名时为 null
     */
    Announcement getActiveAnnouncement(Long userId);

    /**
     * 用户确认公告（不再弹出）
     */
    void ackAnnouncement(Long userId, Long announcementId);

    /**
     * 分页查询全部公告（管理端）
     */
    PageResponse<Announcement> adminListAnnouncements(int pageNum, int pageSize);

    /**
     * 创建公告（管理端）
     */
    Announcement adminCreateAnnouncement(AnnouncementCreateRequest request);

    /**
     * 更新公告（管理端）；已发布公告的标题或内容变更后会重置发布时间并清除用户确认记录
     */
    void adminUpdateAnnouncement(AnnouncementUpdateRequest request);

    /**
     * 删除公告（管理端）
     */
    void adminDeleteAnnouncement(Long announcementId);
}
