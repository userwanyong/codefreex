package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;
import cn.wanyj.codefreex.service.WorkflowImageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 宸ヤ綔娴佸浘鐗囩礌鏉愭湇鍔″疄鐜?
 *
 * @author BanXia
 */
@Service
public class WorkflowImageServiceImpl implements WorkflowImageService {

    private final AppRuntimeConfig.WorkflowProperties workflowProperties;

    public WorkflowImageServiceImpl(AppRuntimeConfig.WorkflowProperties workflowProperties) {
        this.workflowProperties = workflowProperties;
    }

    @Override
    public List<WorkflowImageAsset> collectAssets(String prompt) {
        String keyword = normalizeKeyword(prompt);
        CompletableFuture<WorkflowImageAsset> contentFuture =
                CompletableFuture.supplyAsync(() -> createAsset("content", keyword, "pexels"));
        CompletableFuture<WorkflowImageAsset> illustrationFuture =
                CompletableFuture.supplyAsync(() -> createAsset("illustration", keyword, "undraw"));
        CompletableFuture<WorkflowImageAsset> diagramFuture =
                CompletableFuture.supplyAsync(() -> createAsset("diagram", keyword, "mermaid"));
        CompletableFuture<WorkflowImageAsset> logoFuture =
                CompletableFuture.supplyAsync(() -> createAsset("logo", keyword, "image-model"));

        return List.of(contentFuture.join(), illustrationFuture.join(), diagramFuture.join(), logoFuture.join());
    }

    private WorkflowImageAsset createAsset(String type, String keyword, String source) {
        if (workflowProperties.isImageFetchEnabled()) {
            return new WorkflowImageAsset(type, keyword,
                    "https://assets.codefreex.local/" + type + "/" + keyword.replace(' ', '-'),
                    source);
        }
        return new WorkflowImageAsset(type, keyword,
                "placeholder://" + type + "/" + keyword.replace(' ', '-'),
                source + "-stub");
    }

    private String normalizeKeyword(String prompt) {
        String trimmed = prompt == null ? "app" : prompt.trim();
        if (trimmed.isEmpty()) {
            return "app";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }
}
