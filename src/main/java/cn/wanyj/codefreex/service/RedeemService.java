package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.Redeem;
import cn.wanyj.codefreex.model.entity.RedeemUser;

import java.util.List;

/**
 * @author wanyj
 */
public interface RedeemService {

    /**
     * 生成兑换码（管理员）
     *
     * @param adminId     管理员用户ID
     * @param quota       兑换额度
     * @param batch       批次号（可选）
     * @param expireHours 过期时间（小时，null 表示永不过期）
     * @param maxUseCount 最大使用次数
     * @return 生成的兑换码
     */
    Redeem generateRedeemCode(Long adminId, int quota, String batch, Integer expireHours, Integer maxUseCount);

    /**
     * 使用兑换码
     *
     * @param redeemCode 兑换码
     * @param userId     使用者用户ID
     */
    void useRedeemCode(String redeemCode, Long userId);

    /**
     * 分页查询兑换码列表（管理员）
     */
    PageResponse<Redeem> listRedeems(int pageNum, int pageSize, String status);

    /**
     * 查看兑换码详情
     */
    Redeem getRedeemDetail(Long redeemId);

    /**
     * 查看兑换码使用记录
     */
    List<RedeemUser> listRedeemUsers(Long redeemId);
}
