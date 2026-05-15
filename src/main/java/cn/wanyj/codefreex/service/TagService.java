package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.TagVO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签服务
 *
 * @author wanyj
 */
public interface TagService {

    List<TagVO> listAllTags();

    void createTag(String name, Integer sortOrder);

    void updateTag(Long tagId, String name, Integer sortOrder);

    void deleteTag(Long tagId);

    void setAppTags(Long appId, List<Long> tagIds);

    List<String> getAppTagNames(Long appId);

    Map<Long, List<String>> batchGetAppTagNames(Set<Long> appIds);

    List<String> listFeaturedTagNames();

    /**
     * 根据标签名获取关联的应用ID列表
     */
    Set<Long> getAppIdsByTagName(String tagName);
}
