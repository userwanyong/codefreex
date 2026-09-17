package cn.wanyj.codefreex.service.policy;

/**
 * 邀请码相关码点策略：数值由系统配置（管理端可调）提供。
 *
 * @author wanyj
 */
public final class InviteCreditPolicy {

    private InviteCreditPolicy() {
    }

    /**
     * 计算普通用户创建邀请码的消耗码点
     *
     * @param maxUseCount 邀请码最大可用次数
     * @param costPerUse  每次可用消耗码点（来自系统配置）
     */
    public static int calculateCreateCost(int maxUseCount, int costPerUse) {
        return maxUseCount * costPerUse;
    }
}
