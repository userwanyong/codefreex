package cn.wanyj.codefreex.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.wanyj.codefreex.common.PageResponse;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.dto.request.AppCreateRequest;
import cn.wanyj.codefreex.model.dto.request.AppEditRequest;
import cn.wanyj.codefreex.model.dto.request.AppQueryRequest;
import cn.wanyj.codefreex.model.dto.response.AppVO;
import cn.wanyj.codefreex.model.dto.response.FeaturedAppResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.AppNginxService;
import cn.wanyj.codefreex.service.AppStorageService;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import cn.wanyj.codefreex.service.TagService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;
import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;

/**
 * 应用服务实现
 *
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    private final AppMapper appMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatMemoryService chatMemoryService;
    private final AppStorageService appStorageService;
    private final AppNginxService appNginxService;
    private final UserInfoService userInfoService;
    private final TagService tagService;

    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public App createApp(Long userId, AppCreateRequest request) {
        App app = new App();
        app.setAppName(request.getAppName());
        app.setDescription(request.getDescription());
        app.setInitPrompt(request.getInitPrompt());
        app.setUserId(userId);
        app.setDeployKey("DK_" + IdUtil.getSnowflakeNextIdStr());
        app.setStatus(AppStatus.DRAFT.getValue());
        app.setCodeGenType(request.getCodeGenType() != null && !request.getCodeGenType().isBlank()
                ? CodeGenType.normalize(request.getCodeGenType()).getValue() : null);
        app.setIsPublic(0);
        app.setIsFeatured(0);
        app.setPriority(0);
        app.setViewCount(0);
        app.setLikeCount(0);
        appMapper.insert(app);

        // 设置标签关联
        tagService.setAppTags(app.getId(), request.getTagIds());
        return app;
    }

    @Override
    public void editApp(Long userId, AppEditRequest request) {
        App app = getAppAndCheckOwner(userId, request.getId());

        UpdateChain.of(App.class)
                .where(APP.ID.eq(app.getId()))
                .set(APP.APP_NAME, request.getAppName(), request.getAppName() != null)
                .set(APP.DESCRIPTION, request.getDescription(), request.getDescription() != null)
                .set(APP.COVER, request.getCover(), request.getCover() != null)
                .set(APP.INIT_PROMPT, request.getInitPrompt(), request.getInitPrompt() != null)
                .set(APP.IS_PUBLIC, request.getIsPublic(), request.getIsPublic() != null)
                .set(APP.CODE_GEN_TYPE, CodeGenType.normalize(request.getCodeGenType()).getValue(), request.getCodeGenType() != null)
                .set(APP.EDIT_TIME, LocalDateTime.now())
                .update();

        // 更新标签关联
        if (request.getTagIds() != null) {
            tagService.setAppTags(app.getId(), request.getTagIds());
        }

        // 编辑后将已部署应用设为私有时，自动取消部署
        if (request.getIsPublic() != null && request.getIsPublic() == 0
                && AppStatus.DEPLOYED.getValue().equals(app.getStatus())) {
            appStorageService.deleteDeployedFiles(app.getDeployKey());
            appNginxService.remove(app.getDeployKey());
            UpdateChain.of(App.class)
                    .where(APP.ID.eq(app.getId()))
                    .set(APP.STATUS, AppStatus.GENERATED.getValue())
                    .set(APP.DEPLOYED_TIME, null)
                    .update();
        }
    }

    @Override
    public AppVO getAppById(Long appId, Long userId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }

        // 公开应用任何人可查看；私有应用需要是自己的
        if (app.getIsPublic() == null || app.getIsPublic() != 1) {
            if (userId == null || !userId.equals(app.getUserId())) {
                throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权限查看该应用");
            }
        }
        return toAppVO(app, fillUserInfoMap(Set.of(app.getUserId())), tagService.getAppTagNames(app.getId()));
    }

    @Override
    public void deleteApp(Long userId, Long appId) {
        App app = getAppAndCheckOwner(userId, appId);
        chatHistoryMapper.deleteByQuery(QueryWrapper.create().where(CHAT_HISTORY.APP_ID.eq(appId)));
        chatMemoryService.clearChatMemory(appId, userId);
        appStorageService.deleteGeneratedFiles(app.getDeployKey());
        appStorageService.deleteDeployedFiles(app.getDeployKey());
        appNginxService.remove(app.getDeployKey());
        appMapper.deleteById(appId);
    }

    @Override
    public PageResponse<AppVO> listMyApps(Long userId, int pageNum, int pageSize) {
        pageSize = Math.min(pageSize, 50);

        QueryWrapper query = QueryWrapper.create()
                .where(APP.USER_ID.eq(userId))
                .orderBy(APP.EDIT_TIME.desc());

        com.mybatisflex.core.paginate.Page<App> page = appMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(pageNum, pageSize), query
        );

        List<AppVO> voList = page.getRecords().stream()
                .map(app -> toAppVO(app, fillUserInfoMap(Set.of(app.getUserId())), tagService.getAppTagNames(app.getId())))
                .toList();
        return PageResponse.of(voList, page.getTotalRow(), (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public FeaturedAppResponse listFeaturedApps(String cursor, int size, String tag) {
        size = Math.min(size, 50);

        QueryWrapper query = QueryWrapper.create()
                .where(APP.IS_FEATURED.eq(1))
                .and(APP.IS_PUBLIC.eq(1))
                .and(APP.STATUS.ne(AppStatus.DISABLED.getValue()));

        // 标签筛选（通过关联表）
        if (tag != null && !tag.isEmpty()) {
            Set<Long> appIds = tagService.getAppIdsByTagName(tag);
            if (appIds.isEmpty()) {
                return FeaturedAppResponse.of(Collections.emptyList(), null, false);
            }
            query.and(APP.ID.in(appIds));
        }

        // 解析游标
        if (cursor != null && !cursor.isEmpty()) {
            CursorInfo cursorInfo = parseCursor(cursor);
            query.and(
                    APP.UPDATE_TIME.lt(cursorInfo.time)
                            .or(APP.UPDATE_TIME.eq(cursorInfo.time).and(APP.ID.lt(cursorInfo.id)))
            );
        }

        query.orderBy(APP.PRIORITY.desc(), APP.UPDATE_TIME.desc(), APP.ID.desc());

        // 取 size + 1 条判断是否有下一页
        query.limit(size + 1);

        List<App> apps = appMapper.selectListByQuery(query);

        boolean hasNext = apps.size() > size;
        if (hasNext) {
            apps = apps.subList(0, size);
        }

        // 批量填充用户信息
        Set<Long> userIds = apps.stream().map(App::getUserId).collect(Collectors.toSet());
        Map<Long, UserInfo> userInfoMap = fillUserInfoMap(userIds);

        List<AppVO> voList = apps.stream().map(app -> toAppVO(app, userInfoMap, tagService.getAppTagNames(app.getId()))).toList();

        String nextCursor = null;
        if (hasNext && !apps.isEmpty()) {
            App last = apps.get(apps.size() - 1);
            nextCursor = buildCursor(last.getUpdateTime(), last.getId());
        }

        return FeaturedAppResponse.of(voList, nextCursor, hasNext);
    }

    @Override
    public void setFeatured(Long appId, int featured) {
        if (featured != 0 && featured != 1) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "featured 参数只能为 0 或 1");
        }

        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }

        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.IS_FEATURED, featured)
                .set(APP.UPDATE_TIME, LocalDateTime.now())
                .update();
    }

    @Override
    public PageResponse<App> listAppsForAdmin(AppQueryRequest request) {
        QueryWrapper query = QueryWrapper.create();

        if (request.getStatus() != null && !request.getStatus().isEmpty()) {
            AppStatus.fromValue(request.getStatus());
            query.and(APP.STATUS.eq(request.getStatus()));
        }
        if (request.getAppName() != null && !request.getAppName().isEmpty()) {
            query.and(APP.APP_NAME.like(request.getAppName()));
        }

        query.orderBy(APP.CREATE_TIME.desc());

        com.mybatisflex.core.paginate.Page<App> page = appMapper.paginate(
                new com.mybatisflex.core.paginate.Page<>(request.getPageNum(), request.getPageSize()), query
        );

        return PageResponse.of(page.getRecords(), page.getTotalRow(), (int) page.getPageNumber(), (int) page.getPageSize());
    }

    @Override
    public void updateAppStatus(Long appId, String status) {
        AppStatus.fromValue(status);
        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.STATUS, status)
                .set(APP.EDIT_TIME, LocalDateTime.now())
                .update();
    }

    @Override
    public void updateCodeGenType(Long appId, String codeGenType) {
        UpdateChain.of(App.class)
                .where(APP.ID.eq(appId))
                .set(APP.CODE_GEN_TYPE, codeGenType)
                .update();
    }

    /**
     * 校验应用存在且属于当前用户
     */
    private App getAppAndCheckOwner(Long userId, Long appId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (!app.getUserId().equals(userId)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }

    @Override
    public List<String> listFeaturedTags() {
        return tagService.listFeaturedTagNames();
    }

    /**
     * 批量查询用户信息
     */
    private Map<Long, UserInfo> fillUserInfoMap(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userInfoService.batchGetUserInfos(userIds);
    }

    /**
     * App 转 AppVO（脱敏，含用户信息和标签）
     */
    private AppVO toAppVO(App app, Map<Long, UserInfo> userInfoMap, List<String> tagNames) {
        AppVO vo = new AppVO();
        vo.setId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setDescription(app.getDescription());
        vo.setCover(app.getCover());
        vo.setCodeGenType(app.getCodeGenType());
        vo.setStatus(app.getStatus());
        vo.setIsPublic(app.getIsPublic());
        vo.setIsFeatured(app.getIsFeatured());
        vo.setPriority(app.getPriority());
        vo.setViewCount(app.getViewCount());
        vo.setLikeCount(app.getLikeCount());
        vo.setTags(tagNames != null ? tagNames : Collections.emptyList());
        vo.setUserId(app.getUserId());
        vo.setInitPrompt(app.getInitPrompt());
        vo.setDeployKey(app.getDeployKey());
        vo.setDeployedTime(app.getDeployedTime());
        vo.setEditTime(app.getEditTime());
        vo.setCreateTime(app.getCreateTime());

        // 填充用户信息
        UserInfo userInfo = userInfoMap.get(app.getUserId());
        if (userInfo != null) {
            vo.setUserName(userInfo.getNickname());
            vo.setUserAvatar(userInfo.getAvatar());
        }

        return vo;
    }

    /**
     * 解析游标字符串
     */
    private CursorInfo parseCursor(String cursor) {
        int lastUnderscore = cursor.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "无效的游标参数");
        }
        String timeStr = cursor.substring(0, lastUnderscore);
        String idStr = cursor.substring(lastUnderscore + 1);
        try {
            LocalDateTime time = LocalDateTime.parse(timeStr, CURSOR_TIME_FORMATTER);
            Long id = Long.parseLong(idStr);
            return new CursorInfo(time, id);
        } catch (Exception e) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "无效的游标参数");
        }
    }

    /**
     * 构建游标字符串
     */
    private String buildCursor(LocalDateTime time, Long id) {
        return time.format(CURSOR_TIME_FORMATTER) + "_" + id;
    }

    /**
     * 游标信息
     */
    private record CursorInfo(LocalDateTime time, Long id) {
    }
}
