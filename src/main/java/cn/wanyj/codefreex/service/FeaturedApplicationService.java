package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.entity.FeaturedApplication;

public interface FeaturedApplicationService {

    /**
     * 用户申请精选
     *
     * @param appId  应用ID
     * @param userId 用户ID
     * @param reason 申请理由
     * @return 申请记录
     */
    FeaturedApplication applyFeatured(Long appId, Long userId, String reason);

    /**
     * 管理员分页查询精选申请
     *
     * @param status   状态筛选（可选）
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResponse<FeaturedApplication> listApplications(String status, int pageNum, int pageSize);

    /**
     * 管理员审批精选申请
     *
     * @param applicationId 申请ID
     * @param reviewerId    审核人ID
     * @param approved      是否通过
     * @param adminRemark   管理员备注
     */
    void reviewApplication(Long applicationId, Long reviewerId, boolean approved, String adminRemark);

    /**
     * 查询用户对某应用的最新精选申请状态
     *
     * @param appId  应用ID
     * @param userId 用户ID
     * @return 最新申请记录（可能为null）
     */
    FeaturedApplication getLatestApplication(Long appId, Long userId);

    /**
     * 管理员取消精选（同时更新对应申请记录为 cancelled）
     *
     * @param appId      应用ID
     * @param reviewerId 操作人ID
     */
    void cancelFeatured(Long appId, Long reviewerId);
}
