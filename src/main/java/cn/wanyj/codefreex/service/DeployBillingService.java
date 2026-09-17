package cn.wanyj.codefreex.service;

/**
 * 部署计费服务：对处于已部署状态的应用按计费周期扣减码点，
 * 码点不足以支付下一周期时自动取消部署并站内通知用户
 *
 * @author wanyj
 */
public interface DeployBillingService {

    /**
     * 执行一轮计费：扫描到期应用，扣费或自动下线
     */
    void runBillingCycle();
}
