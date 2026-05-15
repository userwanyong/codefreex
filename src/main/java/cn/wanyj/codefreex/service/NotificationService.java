package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.Notification;

public interface NotificationService {

    void createNotification(Long userId, String title, String content, String type, Long relatedId);

    PageResponse<Notification> listNotifications(Long userId, int pageNum, int pageSize);

    long countUnread(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);
}
