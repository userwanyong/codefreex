package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.NotificationMapper;
import cn.wanyj.codefreex.model.entity.Notification;
import cn.wanyj.codefreex.service.NotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.wanyj.codefreex.model.entity.table.NotificationTableDef.NOTIFICATION;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void createNotification(Long userId, String title, String content, String type, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setRelatedId(relatedId);
        notification.setIsRead(0);
        notification.setIsDelete(0);
        notificationMapper.insert(notification);
    }

    @Override
    public PageResponse<Notification> listNotifications(Long userId, int pageNum, int pageSize) {
        pageSize = Math.min(pageSize, 50);

        QueryWrapper query = QueryWrapper.create()
                .where(NOTIFICATION.USER_ID.eq(userId))
                .and(NOTIFICATION.IS_DELETE.eq(0))
                .orderBy(NOTIFICATION.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<Notification> page = notificationMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(NOTIFICATION.USER_ID.eq(userId))
                        .and(NOTIFICATION.IS_READ.eq(0))
                        .and(NOTIFICATION.IS_DELETE.eq(0))
        );
    }

    @Override
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectOneById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "通知不存在");
        }
        if (notification.getIsRead() == 1) {
            return;
        }
        UpdateChain.of(Notification.class)
                .where(NOTIFICATION.ID.eq(notificationId))
                .set(NOTIFICATION.IS_READ, 1)
                .update();
    }

    @Override
    public void markAllAsRead(Long userId) {
        UpdateChain.of(Notification.class)
                .where(NOTIFICATION.USER_ID.eq(userId))
                .and(NOTIFICATION.IS_READ.eq(0))
                .and(NOTIFICATION.IS_DELETE.eq(0))
                .set(NOTIFICATION.IS_READ, 1)
                .update();
    }
}
