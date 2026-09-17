package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.request.AnnouncementCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AnnouncementUpdateRequest;
import cn.wanyj.codefreex.model.entity.Announcement;
import cn.wanyj.codefreex.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公告接口：首页弹窗公告与管理端维护
 *
 * @author wanyj
 */
@Tag(name = "公告接口")
@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @Operation(summary = "查询当前生效公告（匿名可访问，登录用户已确认不再弹出时返回空）")
    @GetMapping("/active")
    public BaseResponse<Announcement> getActiveAnnouncement() {
        Long userId = UserContext.getLoginUserId();
        return ResultUtils.success(announcementService.getActiveAnnouncement(userId));
    }

    @Operation(summary = "确认公告（不再弹出）")
    @PostMapping("/{announcementId}/ack")
    @AuthCheck
    public BaseResponse<Boolean> ackAnnouncement(@PathVariable Long announcementId) {
        Long userId = UserContext.getLoginUserId();
        announcementService.ackAnnouncement(userId, announcementId);
        return ResultUtils.success(true);
    }

    @Operation(summary = "分页查询公告列表")
    @GetMapping("/admin/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<PageResponse<Announcement>> adminListAnnouncements(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResultUtils.success(announcementService.adminListAnnouncements(pageNum, pageSize));
    }

    @Operation(summary = "创建公告")
    @PostMapping("/admin/create")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Announcement> adminCreateAnnouncement(@Valid @RequestBody AnnouncementCreateRequest request) {
        return ResultUtils.success(announcementService.adminCreateAnnouncement(request));
    }

    @Operation(summary = "更新公告")
    @PostMapping("/admin/update")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> adminUpdateAnnouncement(@Valid @RequestBody AnnouncementUpdateRequest request) {
        announcementService.adminUpdateAnnouncement(request);
        return ResultUtils.success(true);
    }

    @Operation(summary = "删除公告")
    @PostMapping("/admin/delete")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> adminDeleteAnnouncement(@RequestParam Long announcementId) {
        announcementService.adminDeleteAnnouncement(announcementId);
        return ResultUtils.success(true);
    }
}
