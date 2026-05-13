package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.model.dto.request.AppCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AppEditRequest;
import cn.wanyj.codefreex.model.dto.request.AppQueryRequest;
import cn.wanyj.codefreex.model.dto.response.AppVO;
import cn.wanyj.codefreex.model.dto.response.FeaturedAppResponse;
import cn.wanyj.codefreex.model.entity.App;

import java.util.List;

/**
 * 应用服务接口
 *
 * @author wanyj
 */
public interface AppService {

    /**
     * 创建应用（A-01）
     *
     * @param userId  用户ID
     * @param request 创建请求
     * @return 创建的应用
     */
    App createApp(Long userId, AppCreateRequest request);

    /**
     * 编辑应用（A-02）
     *
     * @param userId  用户ID
     * @param request 编辑请求
     */
    void editApp(Long userId, AppEditRequest request);

    /**
     * 查看应用详情（A-03）
     *
     * @param appId  应用ID
     * @param userId 当前用户ID（可为null，未登录时）
     * @return 应用详情
     */
    AppVO getAppById(Long appId, Long userId);

    /**
     * 删除应用（A-04）
     *
     * @param userId 用户ID
     * @param appId  应用ID
     */
    void deleteApp(Long userId, Long appId);

    /**
     * 分页查询我的应用（A-05）
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResponse<AppVO> listMyApps(Long userId, int pageNum, int pageSize);

    /**
     * 游标分页查询精选应用（A-06，公开）
     *
     * @param cursor 游标（null 表示首页）
     * @param size   每页大小
     * @param tag    标签筛选（可选）
     * @return 精选应用响应
     */
    FeaturedAppResponse listFeaturedApps(String cursor, int size, String tag);

    /**
     * 获取精选应用的所有标签（去重）
     */
    List<String> listFeaturedTags();

    /**
     * 设置/取消精选（A-13，管理员）
     *
     * @param appId    应用ID
     * @param featured 是否精选（1-是 0-否）
     */
    void setFeatured(Long appId, int featured);

    /**
     * 管理员分页查询应用（A-12）
     *
     * @param request 查询请求
     * @return 分页结果
     */
    PageResponse<App> listAppsForAdmin(AppQueryRequest request);

    /**
     * 更新应用状态
     *
     * @param appId  应用ID
     * @param status 新状态
     */
    void updateAppStatus(Long appId, String status);

    /**
     * 更新应用代码生成类型
     *
     * @param appId       应用ID
     * @param codeGenType 代码生成类型
     */
    void updateCodeGenType(Long appId, String codeGenType);
}
