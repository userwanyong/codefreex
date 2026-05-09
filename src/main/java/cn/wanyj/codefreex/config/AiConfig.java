package cn.wanyj.codefreex.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AI 配置类
 *
 * @author wanyj
 */
@Configuration
public class AiConfig {

    /**
     * 主生成模型 - 流式（多例，解决并发阻塞）
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModel(AiModelProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses())
                .build();
    }

    /**
     * 预审核模型 - 同步（低成本、快速响应）
     */
    @Bean
    public ChatModel reviewChatModel(AiReviewProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .build();
    }

    /**
     * 提示词加载工具
     */
    @Bean
    public PromptLoader promptLoader(ResourceLoader resourceLoader, AiPromptsProperties properties) {
        return new PromptLoader(resourceLoader, properties.getPromptsDir());
    }

    /**
     * 主模型配置属性
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
    public static class AiModelProperties {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private double temperature = 0.7;
        private int maxTokens = 16384;
        private boolean logRequests = false;
        private boolean logResponses = false;
    }

    /**
     * 预审核模型配置属性
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "ai.review")
    public static class AiReviewProperties {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private double temperature = 0.3;
        private int maxTokens = 2048;
    }

    /**
     * 提示词目录配置
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "ai")
    public static class AiPromptsProperties {
        private String promptsDir;
    }

    /**
     * 提示词加载器
     */
    public static class PromptLoader {

        private final ResourceLoader resourceLoader;
        private final String promptsDir;

        public PromptLoader(ResourceLoader resourceLoader, String promptsDir) {
            this.resourceLoader = resourceLoader;
            this.promptsDir = promptsDir;
        }

        /**
         * 加载提示词文件内容
         *
         * @param filename 文件名（如 html_single.txt）
         * @return 提示词内容
         */
        public String load(String filename) {
            try {
                Resource resource = resourceLoader.getResource(promptsDir + filename);
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载提示词文件失败: " + filename, e);
            }
        }
    }
}
