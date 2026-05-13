package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppTagMapper;
import cn.wanyj.codefreex.mapper.TagMapper;
import cn.wanyj.codefreex.model.dto.response.TagVO;
import cn.wanyj.codefreex.model.entity.AppTag;
import cn.wanyj.codefreex.model.entity.Tag;
import cn.wanyj.codefreex.service.TagService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static cn.wanyj.codefreex.model.entity.table.AppTableDef.APP;
import static cn.wanyj.codefreex.model.entity.table.AppTagTableDef.APP_TAG;
import static cn.wanyj.codefreex.model.entity.table.TagTableDef.TAG;

/**
 * 标签服务实现
 *
 * @author wanyj
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final AppTagMapper appTagMapper;

    @Override
    public List<TagVO> listAllTags() {
        QueryWrapper query = QueryWrapper.create()
                .where(TAG.IS_DELETE.eq(0))
                .orderBy(TAG.SORT_ORDER.asc(), TAG.ID.asc());

        List<Tag> tags = tagMapper.selectListByQuery(query);

        // 查询每个标签关联的应用数
        List<TagVO> voList = new ArrayList<>();
        for (Tag tag : tags) {
            TagVO vo = new TagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            vo.setSortOrder(tag.getSortOrder());

            long count = appTagMapper.selectCountByQuery(
                    QueryWrapper.create().where(APP_TAG.TAG_ID.eq(tag.getId())).and(APP_TAG.IS_DELETE.eq(0))
            );
            vo.setAppCount((int) count);
            voList.add(vo);
        }
        return voList;
    }

    @Override
    public void createTag(String name, Integer sortOrder) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "标签名称不能为空");
        }
        name = name.trim();

        // 检查名称唯一性
        long count = tagMapper.selectCountByQuery(
                QueryWrapper.create().where(TAG.NAME.eq(name)).and(TAG.IS_DELETE.eq(0))
        );
        if (count > 0) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setSortOrder(sortOrder != null ? sortOrder : 0);
        tagMapper.insert(tag);
    }

    @Override
    public void updateTag(Long tagId, String name, Integer sortOrder) {
        Tag tag = tagMapper.selectOneById(tagId);
        if (tag == null || tag.getIsDelete() == 1) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "标签不存在");
        }

        if (name != null && !name.trim().isEmpty()) {
            name = name.trim();
            // 检查名称唯一性（排除自身）
            long count = tagMapper.selectCountByQuery(
                    QueryWrapper.create().where(TAG.NAME.eq(name)).and(TAG.IS_DELETE.eq(0)).and(TAG.ID.ne(tagId))
            );
            if (count > 0) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "标签名称已存在");
            }
            tag.setName(name);
        }

        if (sortOrder != null) {
            tag.setSortOrder(sortOrder);
        }

        tagMapper.update(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId) {
        Tag tag = tagMapper.selectOneById(tagId);
        if (tag == null || tag.getIsDelete() == 1) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "标签不存在");
        }

        // 软删标签
        tag.setIsDelete(1);
        tagMapper.update(tag);

        // 软删关联
        List<AppTag> relations = appTagMapper.selectListByQuery(
                QueryWrapper.create().where(APP_TAG.TAG_ID.eq(tagId)).and(APP_TAG.IS_DELETE.eq(0))
        );
        for (AppTag at : relations) {
            at.setIsDelete(1);
            appTagMapper.update(at);
        }
    }

    @Override
    @Transactional
    public void setAppTags(Long appId, List<Long> tagIds) {
        if (tagIds == null) {
            tagIds = Collections.emptyList();
        }
        if (tagIds.size() > 3) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "标签数量不能超过3个");
        }

        // 校验所有 tagId 存在
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagMapper.selectListByIds(tagIds);
            Map<Long, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, t -> t));
            for (Long tagId : tagIds) {
                Tag t = tagMap.get(tagId);
                if (t == null || t.getIsDelete() == 1) {
                    throw new BusinessException(ResponseCode.PARAMS_ERROR, "标签不存在: " + tagId);
                }
            }
        }

        // 软删现有关联
        List<AppTag> existing = appTagMapper.selectListByQuery(
                QueryWrapper.create().where(APP_TAG.APP_ID.eq(appId)).and(APP_TAG.IS_DELETE.eq(0))
        );
        for (AppTag at : existing) {
            at.setIsDelete(1);
            appTagMapper.update(at);
        }

        // 插入新关联
        for (Long tagId : tagIds) {
            AppTag at = new AppTag();
            at.setAppId(appId);
            at.setTagId(tagId);
            appTagMapper.insert(at);
        }
    }

    @Override
    public List<String> getAppTagNames(Long appId) {
        // 查询关联的 tagId
        List<AppTag> relations = appTagMapper.selectListByQuery(
                QueryWrapper.create().where(APP_TAG.APP_ID.eq(appId)).and(APP_TAG.IS_DELETE.eq(0))
        );
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = relations.stream().map(AppTag::getTagId).toList();
        List<Tag> tags = tagMapper.selectListByIds(tagIds);
        return tags.stream()
                .filter(t -> t.getIsDelete() == 0)
                .sorted(Comparator.comparingInt(t -> t.getSortOrder() != null ? t.getSortOrder() : 0))
                .map(Tag::getName)
                .toList();
    }

    @Override
    public Map<Long, List<String>> batchGetAppTagNames(Set<Long> appIds) {
        if (appIds == null || appIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 查询所有关联
        List<AppTag> relations = appTagMapper.selectListByQuery(
                QueryWrapper.create().where(APP_TAG.APP_ID.in(appIds)).and(APP_TAG.IS_DELETE.eq(0))
        );
        if (relations.isEmpty()) {
            Map<Long, List<String>> result = new HashMap<>();
            for (Long appId : appIds) {
                result.put(appId, Collections.emptyList());
            }
            return result;
        }

        // 收集所有 tagId，批量查询 Tag
        Set<Long> tagIds = relations.stream().map(AppTag::getTagId).collect(Collectors.toSet());
        List<Tag> tags = tagMapper.selectListByIds(new ArrayList<>(tagIds));
        Map<Long, Tag> tagMap = tags.stream().filter(t -> t.getIsDelete() == 0)
                .collect(Collectors.toMap(Tag::getId, t -> t));

        // 按 appId 分组
        Map<Long, List<String>> result = new HashMap<>();
        for (Long appId : appIds) {
            result.put(appId, new ArrayList<>());
        }
        for (AppTag at : relations) {
            Tag tag = tagMap.get(at.getTagId());
            if (tag != null) {
                result.computeIfAbsent(at.getAppId(), k -> new ArrayList<>()).add(tag.getName());
            }
        }
        return result;
    }

    @Override
    public List<String> listFeaturedTagNames() {
        // 查询精选公开应用的 appId
        List<AppTag> relations = appTagMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(APP_TAG.IS_DELETE.eq(0))
                        .and(APP_TAG.APP_ID.in(
                                QueryWrapper.create()
                                        .select(APP.ID)
                                        .from(APP)
                                        .where(APP.IS_FEATURED.eq(1))
                                        .and(APP.IS_PUBLIC.eq(1))
                                        .and(APP.STATUS.ne("disabled"))
                                        .and(APP.IS_DELETE.eq(0))
                        ))
        );

        if (relations.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> tagIds = relations.stream().map(AppTag::getTagId).collect(Collectors.toSet());
        List<Tag> tags = tagMapper.selectListByIds(new ArrayList<>(tagIds));
        return tags.stream()
                .filter(t -> t.getIsDelete() == 0)
                .sorted(Comparator.comparingInt(t -> t.getSortOrder() != null ? t.getSortOrder() : 0))
                .map(Tag::getName)
                .distinct()
                .toList();
    }

    @Override
    public Set<Long> getAppIdsByTagName(String tagName) {
        // 先查 tagId
        Tag tag = tagMapper.selectOneByQuery(
                QueryWrapper.create().where(TAG.NAME.eq(tagName)).and(TAG.IS_DELETE.eq(0))
        );
        if (tag == null) {
            return Collections.emptySet();
        }

        // 查关联的 appId
        List<AppTag> relations = appTagMapper.selectListByQuery(
                QueryWrapper.create().where(APP_TAG.TAG_ID.eq(tag.getId())).and(APP_TAG.IS_DELETE.eq(0))
        );
        return relations.stream().map(AppTag::getAppId).collect(Collectors.toSet());
    }
}
