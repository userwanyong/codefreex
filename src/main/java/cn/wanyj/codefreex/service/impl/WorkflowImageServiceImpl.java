package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;
import cn.wanyj.codefreex.service.OssService;
import cn.wanyj.codefreex.service.WorkflowImageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 工作流图片素材服务实现
 *
 * @author BanXia
 */
@Slf4j
@Service
public class WorkflowImageServiceImpl implements WorkflowImageService {

    private final AppRuntimeConfig.WorkflowProperties workflowProperties;
    private final OssService ossService;
    private final ChatModel reviewChatModel;
    private final AiConfig.PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public WorkflowImageServiceImpl(AppRuntimeConfig.WorkflowProperties workflowProperties,
                                    OssService ossService,
                                    @Qualifier("reviewChatModel") ChatModel reviewChatModel,
                                    AiConfig.PromptLoader promptLoader,
                                    ObjectMapper objectMapper) {
        this.workflowProperties = workflowProperties;
        this.ossService = ossService;
        this.reviewChatModel = reviewChatModel;
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WorkflowImageAsset> collectAssets(String prompt) {
        String keyword = normalizeKeyword(prompt);
        CompletableFuture<WorkflowImageAsset> contentFuture =
                CompletableFuture.supplyAsync(() -> createPlaceholderAsset("content", keyword));
        CompletableFuture<WorkflowImageAsset> illustrationFuture =
                CompletableFuture.supplyAsync(() -> createPlaceholderAsset("illustration", keyword));
        CompletableFuture<WorkflowImageAsset> diagramFuture =
                CompletableFuture.supplyAsync(() -> createPlaceholderAsset("diagram", keyword));
        CompletableFuture<WorkflowImageAsset> logoFuture =
                CompletableFuture.supplyAsync(() -> createPlaceholderAsset("logo", keyword));

        return List.of(contentFuture.join(), illustrationFuture.join(), diagramFuture.join(), logoFuture.join());
    }

    @Override
    public WorkflowImageAsset fetchSingleAsset(String type, String keyword, String mermaidCode, String description) {
        if (!workflowProperties.isImageFetchEnabled()) {
            return createPlaceholderAsset(type, keyword);
        }
        try {
            return switch (type) {
                case "content" -> fetchContentImage(keyword);
                case "illustration" -> fetchIllustrationImage(keyword);
                case "diagram" -> fetchDiagramImage(keyword, mermaidCode);
                case "logo" -> fetchLogoImage(keyword, description);
                default -> createPlaceholderAsset(type, keyword);
            };
        } catch (Exception e) {
            log.warn("获取 {} 类型图片失败, keyword='{}', 回退到占位图: {}", type, keyword, e.getMessage());
            return createPlaceholderAsset(type, keyword);
        }
    }

    private WorkflowImageAsset fetchContentImage(String keyword) {
        String apiKey = workflowProperties.getPexelsApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Pexels API key 未配置");
        }
        String url = "https://api.pexels.com/v1/search?query="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&per_page=3&orientation=landscape";

        String body;
        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", apiKey)
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                throw new RuntimeException("Pexels API 返回状态码: " + response.getStatus());
            }
            body = response.body();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode photos = root.path("photos");
            if (photos.isArray() && !photos.isEmpty()) {
                String imageUrl = photos.get(0).path("src").path("large").asText("");
                if (!imageUrl.isBlank()) {
                    log.info("Pexels 获取成功, keyword='{}', url={}", keyword, imageUrl);
                    return new WorkflowImageAsset("content", keyword, imageUrl, "pexels");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Pexels 响应解析失败: " + e.getMessage(), e);
        }
        throw new RuntimeException("Pexels 未找到结果: " + keyword);
    }

    private WorkflowImageAsset fetchIllustrationImage(String keyword) {
        String apiKey = workflowProperties.getPixabayApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Pixabay API key 未配置");
        }
        String url = "https://pixabay.com/api/?key=" + apiKey
                + "&q=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&image_type=illustration&per_page=3";

        String body;
        try (HttpResponse response = HttpRequest.get(url)
                .timeout(5000)
                .execute()) {
            if (!response.isOk()) {
                throw new RuntimeException("Pixabay API 返回状态码: " + response.getStatus());
            }
            body = response.body();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode hits = root.path("hits");
            if (hits.isArray() && !hits.isEmpty()) {
                String imageUrl = hits.get(0).path("webformatURL").asText("");
                if (!imageUrl.isBlank()) {
                    log.info("Pixabay 获取成功, keyword='{}', url={}", keyword, imageUrl);
                    return new WorkflowImageAsset("illustration", keyword, imageUrl, "pixabay");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Pixabay 响应解析失败: " + e.getMessage(), e);
        }
        throw new RuntimeException("Pixabay 未找到结果: " + keyword);
    }

    private WorkflowImageAsset fetchDiagramImage(String keyword, String mermaidCode) {
        if (mermaidCode == null || mermaidCode.isBlank()) {
            throw new RuntimeException("未提供 Mermaid 代码");
        }

        String base64 = Base64.getUrlEncoder().encodeToString(
                mermaidCode.getBytes(StandardCharsets.UTF_8));
        String renderUrl = "https://mermaid.ink/img/base64:" + base64;

        byte[] pngData;
        try (HttpResponse response = HttpRequest.get(renderUrl)
                .timeout(10000)
                .execute()) {
            pngData = response.bodyBytes();
        }

        if (pngData == null || pngData.length == 0) {
            throw new RuntimeException("Mermaid 渲染返回空图片");
        }

        String objectKey = "workflow/diagram/" + normalizeKeyword(keyword).replace(' ', '-')
                + "-" + System.currentTimeMillis() + ".png";
        String ossUrl = ossService.upload(objectKey, pngData, "image/png");
        log.info("Mermaid 图表渲染并上传成功, keyword='{}', ossUrl={}", keyword, ossUrl);
        return new WorkflowImageAsset("diagram", keyword, ossUrl, "mermaid");
    }

    private WorkflowImageAsset fetchLogoImage(String keyword, String description) {
        String prompt = promptLoader.load("workflow_svg_logo.txt").formatted(
                description != null ? description : "简约通用Logo，品牌名称: " + keyword);

        ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
        String svgCode = extractSvg(response.aiMessage().text().trim());

        if (svgCode.isBlank() || !svgCode.startsWith("<svg")) {
            throw new RuntimeException("AI 未返回有效的 SVG");
        }

        String objectKey = "workflow/logo/" + normalizeKeyword(keyword).replace(' ', '-')
                + "-" + System.currentTimeMillis() + ".svg";
        String ossUrl = ossService.upload(objectKey,
                svgCode.getBytes(StandardCharsets.UTF_8), "image/svg+xml");
        log.info("AI Logo 生成并上传成功, keyword='{}', ossUrl={}", keyword, ossUrl);
        return new WorkflowImageAsset("logo", keyword, ossUrl, "ai-svg");
    }

    private String extractSvg(String text) {
        int start = text.indexOf("<svg");
        int end = text.lastIndexOf("</svg>");
        if (start >= 0 && end > start) {
            return text.substring(start, end + 6);
        }
        return text;
    }

    private WorkflowImageAsset createPlaceholderAsset(String type, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword).replace(' ', '-');
        if (workflowProperties.isImageFetchEnabled()) {
            return new WorkflowImageAsset(type, keyword,
                    "https://assets.codefreex.local/" + type + "/" + normalizedKeyword,
                    type + "-placeholder");
        }
        return new WorkflowImageAsset(type, keyword,
                "placeholder://" + type + "/" + normalizedKeyword,
                type + "-stub");
    }

    private String normalizeKeyword(String prompt) {
        String trimmed = prompt == null ? "app" : prompt.trim();
        if (trimmed.isEmpty()) {
            return "app";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }
}
