package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.entity.Notification;
import cn.wanyj.codefreex.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "通知接口")
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "查询通知列表")
    @GetMapping("/list")
    public BaseResponse<PageResponse<Notification>> listNotifications(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(notificationService.listNotifications(userId, pageNum, pageSize));
    }

    @Operation(summary = "查询未读通知数量")
    @GetMapping("/unread-count")
    public BaseResponse<Long> getUnreadCount() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(notificationService.countUnread(userId));
    }

    @Operation(summary = "标记通知为已读")
    @PostMapping("/read/{id}")
    public BaseResponse<Boolean> markAsRead(@PathVariable Long id) {
        Long userId = UserContext.getLoginUserId();
        notificationService.markAsRead(id, userId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "全部标记为已读")
    @PostMapping("/read-all")
    public BaseResponse<Boolean> markAllAsRead() {
        Long userId = UserContext.getLoginUserId();
        notificationService.markAllAsRead(userId);
        return ResultUtils.success(true);
    }
}
