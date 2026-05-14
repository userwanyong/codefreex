package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.FeaturedApplicationMapper;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.FeaturedApplication;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.FeaturedApplicationService;
import cn.wanyj.codefreex.service.NotificationService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.wanyj.codefreex.model.entity.table.FeaturedApplicationTableDef.FEATURED_APPLICATION;

@Service
@RequiredArgsConstructor
public class FeaturedApplicationServiceImpl implements FeaturedApplicationService {

    private final FeaturedApplicationMapper featuredApplicationMapper;
    private final AppMapper appMapper;
    @Lazy
    private final AppService appService;
    private final NotificationService notificationService;

    @Override
    public FeaturedApplication applyFeatured(Long appId, Long userId, String reason) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (!app.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "只能申请自己的应用");
        }
        if (!AppStatus.DEPLOYED.getValue().equals(app.getStatus())) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "只有已部署的应用才能申请精选");
        }
        if (app.getIsFeatured() != null && app.getIsFeatured() == 1) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "该应用已经是精选应用");
        }

        // 检查是否有待审核的申请
        FeaturedApplication pending = featuredApplicationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(FEATURED_APPLICATION.APP_ID.eq(appId))
                        .and(FEATURED_APPLICATION.STATUS.eq("pending"))
        );
        if (pending != null) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "该应用已有待审核的精选申请");
        }

        FeaturedApplication application = new FeaturedApplication();
        application.setAppId(appId);
        application.setUserId(userId);
        application.setReason(reason);
        application.setStatus("pending");
        featuredApplicationMapper.insert(application);
        return application;
    }

    @Override
    public PageResponse<FeaturedApplication> listApplications(String status, int pageNum, int pageSize) {
        pageSize = Math.min(pageSize, 50);

        QueryWrapper query = QueryWrapper.create();
        if (status != null && !status.isEmpty()) {
            query.and(FEATURED_APPLICATION.STATUS.eq(status));
        }
        query.orderBy(FEATURED_APPLICATION.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<FeaturedApplication> page = featuredApplicationMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(),
                (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewApplication(Long applicationId, Long reviewerId, boolean approved, String adminRemark) {
        FeaturedApplication application = featuredApplicationMapper.selectOneById(applicationId);
        if (application == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "申请不存在");
        }
        if (!"pending".equals(application.getStatus()) && !(approved && ("rejected".equals(application.getStatus()) || "cancelled".equals(application.getStatus())))) {
            throw new BusinessException(ResponseCode.OPERATION_ERROR, "该申请已处理");
        }

        String newStatus = approved ? "approved" : "rejected";
        UpdateChain.of(FeaturedApplication.class)
                .where(FEATURED_APPLICATION.ID.eq(applicationId))
                .set(FEATURED_APPLICATION.STATUS, newStatus)
                .set(FEATURED_APPLICATION.ADMIN_REMARK, adminRemark)
                .set(FEATURED_APPLICATION.REVIEWER_ID, reviewerId)
                .set(FEATURED_APPLICATION.REVIEW_TIME, LocalDateTime.now())
                .update();

        // 审批通过时，将应用设为精选
        if (approved) {
            appService.setFeatured(application.getAppId(), 1);
        }

        // 发送通知给申请人
        String appName = appMapper.selectOneById(application.getAppId()) != null
                ? appMapper.selectOneById(application.getAppId()).getAppName() : "应用";
        String title = approved ? "精选申请已通过" : "精选申请已拒绝";
        String content = approved
                ? "你的应用「" + appName + "」的精选申请已通过，该应用已被设为精选。"
                : "你的应用「" + appName + "」的精选申请已被拒绝。"
                  + (adminRemark != null && !adminRemark.isBlank() ? "管理员备注：" + adminRemark : "你可以修改后重新申请。");
        notificationService.createNotification(application.getUserId(), title, content, "featured_review", applicationId);
    }

    @Override
    public FeaturedApplication getLatestApplication(Long appId, Long userId) {
        return featuredApplicationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(FEATURED_APPLICATION.APP_ID.eq(appId))
                        .and(FEATURED_APPLICATION.USER_ID.eq(userId))
                        .orderBy(FEATURED_APPLICATION.CREATE_TIME.desc())
                        .limit(1)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFeatured(Long appId, Long reviewerId) {
        // 取消应用精选状态
        appService.setFeatured(appId, 0);

        // 将该应用最新的 approved 申请记录更新为 cancelled
        FeaturedApplication approved = featuredApplicationMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(FEATURED_APPLICATION.APP_ID.eq(appId))
                        .and(FEATURED_APPLICATION.STATUS.eq("approved"))
                        .orderBy(FEATURED_APPLICATION.CREATE_TIME.desc())
                        .limit(1)
        );
        if (approved != null) {
            UpdateChain.of(FeaturedApplication.class)
                    .where(FEATURED_APPLICATION.ID.eq(approved.getId()))
                    .set(FEATURED_APPLICATION.STATUS, "cancelled")
                    .set(FEATURED_APPLICATION.REVIEWER_ID, reviewerId)
                    .set(FEATURED_APPLICATION.REVIEW_TIME, LocalDateTime.now())
                    .update();

            // 通知申请人
            String appName = appMapper.selectOneById(appId) != null
                    ? appMapper.selectOneById(appId).getAppName() : "应用";
            notificationService.createNotification(approved.getUserId(),
                    "精选已被取消",
                    "你的应用「" + appName + "」的精选状态已被管理员取消。",
                    "featured_review", approved.getId());
        }
    }
}
