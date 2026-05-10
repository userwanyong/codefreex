package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;

import java.util.List;

/**
 * 工作流图片素材服务
 *
 * @author BanXia
 */
public interface WorkflowImageService {

    List<WorkflowImageAsset> collectAssets(String prompt);
}
