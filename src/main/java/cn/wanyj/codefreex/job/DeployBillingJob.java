package cn.wanyj.codefreex.job;

import cn.wanyj.codefreex.service.DeployBillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 部署计费定时任务：每分钟检查一次到期应用（实际计费周期由系统配置决定，
 * 默认 60 分钟即按小时计费），码点不足的应用会被自动取消部署。
 *
 * @author wanyj
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeployBillingJob {

    private final DeployBillingService deployBillingService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void runBillingCycle() {
        try {
            deployBillingService.runBillingCycle();
        } catch (Exception e) {
            log.error("部署计费定时任务执行失败", e);
        }
    }
}
