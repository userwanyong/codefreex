package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.model.enums.SystemConfigKey;
import cn.wanyj.codefreex.service.AppDeployService;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.DeployBillingService;
import cn.wanyj.codefreex.service.NotificationService;
import cn.wanyj.codefreex.service.SystemConfigService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;

/**
 * 部署计费服务实现：计费周期与周期消耗码点均由系统配置（管理端可调）。
 * 每个应用以 deploy_billed_time 记录最近扣费时间，到期扣一个周期的码点；
 * 余额不足以支付下一周期时自动取消部署并发送站内通知。
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployBillingServiceImpl implements DeployBillingService {

    /**
     * 部署自动取消通知类型（前端通知中心按字符串展示）
     */
    private static final String NOTIFY_TYPE_DEPLOY_BILLING = "deploy_billing";

    private final AppMapper appMapper;
    private final UserInfoService userInfoService;
    private final CreditTransactionService creditTransactionService;
    private final AppDeployService appDeployService;
    private final NotificationService notificationService;
    private final SystemConfigService systemConfigService;

    @Override
    public void runBillingCycle() {
        int cycleCost = systemConfigService.getInt(SystemConfigKey.CREDIT_DEPLOY_HOURLY_COST);
        if (cycleCost <= 0) {
            // 部署计费关闭
            return;
        }
        int intervalMinutes = Math.max(1,
                systemConfigService.getInt(SystemConfigKey.CREDIT_DEPLOY_BILLING_INTERVAL_MINUTES));
        LocalDateTime dueTime = LocalDateTime.now().minusMinutes(intervalMinutes);

        List<App> deployedApps = appMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APP.STATUS.eq(AppStatus.DEPLOYED.getValue()))
                        .and(APP.IS_DELETE.eq(0))
        );
        for (App app : deployedApps) {
            try {
                // 精选应用部署期间免计费：只推进计费时钟，不扣减码点
                if (app.getIsFeatured() != null && app.getIsFeatured() == 1) {
                    if (app.getDeployBilledTime() == null || !app.getDeployBilledTime().isAfter(dueTime)) {
                        markBilled(app.getId());
                    }
                    continue;
                }
                // 存量部署（功能上线前已部署，无计费时间）不追溯扣费，仅从当前周期起算
                if (app.getDeployBilledTime() == null) {
                    markBilled(app.getId());
                    continue;
                }
                if (app.getDeployBilledTime().isAfter(dueTime)) {
                    continue;
                }
                chargeOrCancel(app, cycleCost, intervalMinutes);
            } catch (Exception e) {
                // 单个应用异常不影响其余应用计费
                log.error("部署计费处理异常, appId={}", app.getId(), e);
            }
        }
    }

    /**
     * 扣减一个周期的部署码点；码点不足时自动取消部署并通知用户
     */
    private void chargeOrCancel(App app, int cycleCost, int intervalMinutes) {
        try {
            int balanceAfter = userInfoService.deductCredits(app.getUserId(), cycleCost);
            creditTransactionService.recordTransaction(
                    app.getUserId(),
                    CreditTransactionType.CONSUME,
                    -cycleCost,
                    balanceAfter,
                    CreditSourceType.DEPLOY,
                    app.getId(),
                    "部署计费（周期 " + intervalMinutes + " 分钟）",
                    null
            );
            markBilled(app.getId());
        } catch (BusinessException e) {
            // 码点不足以支持下一周期：自动取消部署并站内通知
            log.warn("应用部署因码点不足自动取消, appId={}, userId={}, reason={}",
                    app.getId(), app.getUserId(), e.getMessage());
            autoCancelDeploy(app, cycleCost, intervalMinutes);
        }
    }

    private void autoCancelDeploy(App app, int cycleCost, int intervalMinutes) {
        try {
            appDeployService.cancelDeploy(app.getUserId(), app.getId());
        } catch (Exception e) {
            log.error("自动取消部署失败, appId={}", app.getId(), e);
            return;
        }
        String appName = app.getAppName() != null ? app.getAppName() : String.valueOf(app.getId());
        String periodText = intervalMinutes % 60 == 0 && intervalMinutes >= 60
                ? (intervalMinutes / 60) + "小时" : intervalMinutes + "分钟";
        notificationService.createNotification(
                app.getUserId(),
                "部署已自动取消（码点不足）",
                "您的应用《" + appName + "》因码点不足已被系统自动取消部署"
                        + "（每 " + periodText + " 消耗 " + cycleCost + " 码点）。"
                        + "兑换码点后可在应用详情页重新部署。",
                NOTIFY_TYPE_DEPLOY_BILLING,
                app.getId()
        );
    }

    private void markBilled(Long appId) {
        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.DEPLOY_BILLED_TIME, LocalDateTime.now())
                .update();
    }
}
