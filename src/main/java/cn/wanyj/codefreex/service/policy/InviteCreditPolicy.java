package cn.wanyj.codefreex.service.policy;

/**
 * 邀请码相关码点策略。
 *
 * @author wanyj
 */
public final class InviteCreditPolicy {

    public static final int INVITE_REWARD_CREDITS = 100;
    public static final int INVITE_CREATE_COST_PER_USE = 50;

    private InviteCreditPolicy() {
    }

    public static int calculateCreateCost(int maxUseCount) {
        return maxUseCount * INVITE_CREATE_COST_PER_USE;
    }
}
