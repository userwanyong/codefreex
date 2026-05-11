package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;

import java.util.List;

/**
 * 工作流图片素材服务
 *
 * @author BanXia
 */
public interface WorkflowImageService {

    /**
     * 兜底方式：根据 prompt 生成占位图片素材
     */
    List<WorkflowImageAsset> collectAssets(String prompt);

    /**
     * 按类型获取单个真实图片素材
     *
     * @param type        图片类型 (content/illustration/diagram/logo)
     * @param keyword     关键词
     * @param mermaidCode Mermaid 代码（diagram 类型使用）
     * @param description SVG 设计描述（logo 类型使用）
     * @return 图片素材，获取失败时返回占位素材
     */
    WorkflowImageAsset fetchSingleAsset(String type, String keyword, String mermaidCode, String description);
}
