package cn.wanyj.codefreex.service;

import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;

import java.util.List;

/**
 * 宸ヤ綔娴佸浘鐗囩礌鏉愭湇鍔?
 *
 * @author BanXia
 */
public interface WorkflowImageService {

    List<WorkflowImageAsset> collectAssets(String prompt);
}
