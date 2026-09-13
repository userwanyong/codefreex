package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 码点计费配置视图（面向用户展示的价格信息，不含敏感项）
 *
 * @author wanyj
 */
@Data
public class CreditConfigVO {

    /**
     * 创建应用（首次生成）消耗码点
     */
    private Integer firstGenerateCost;

    /**
     * 每轮对话消耗码点
     */
    private Integer chatRoundCost;

    /**
     * 邀请注册奖励码点
     */
    private Integer inviteReward;

    /**
     * 普通用户创建邀请码每可用次数消耗码点
     */
    private Integer inviteCreateCostPerUse;

    /**
     * 部署每个计费周期消耗码点（0 表示关闭部署计费）
     */
    private Integer deployHourlyCost;

    /**
     * 部署计费周期（分钟，默认 60 即按小时）
     */
    private Integer deployBillingIntervalMinutes;
}
