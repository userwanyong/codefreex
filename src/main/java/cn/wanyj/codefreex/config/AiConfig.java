package cn.wanyj.codefreex.config;

import cn.wanyj.codefreex.model.enums.SystemConfigKey;
import cn.wanyj.codefreex.service.SystemConfigService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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
import java.time.Duration;

/**
 * AI 配置类：模型连接信息（密钥/地址/模型名等）优先取系统配置（管理端可热更），
 * yml 属性仅作为首次启动的播种默认值。
 *
 * @author wanyj
 */
@Configuration
public class AiConfig {

    /**
     * 主生成模型 - 流式（多例，解决并发阻塞）；多例配合系统配置实现每次获取均读取最新配置
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModel(SystemConfigService systemConfigService) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(systemConfigService.getString(SystemConfigKey.AI_MAIN_API_KEY))
                .baseUrl(systemConfigService.getString(SystemConfigKey.AI_MAIN_BASE_URL))
                .modelName(systemConfigService.getString(SystemConfigKey.AI_MAIN_MODEL_NAME))
                .temperature(systemConfigService.getDouble(SystemConfigKey.AI_MAIN_TEMPERATURE))
                .maxTokens(systemConfigService.getInt(SystemConfigKey.AI_MAIN_MAX_TOKENS))
                .logRequests(systemConfigService.getBoolean(SystemConfigKey.AI_MAIN_LOG_REQUESTS))
                .logResponses(systemConfigService.getBoolean(SystemConfigKey.AI_MAIN_LOG_RESPONSES))
                .build();
    }

    /**
     * 预审核模型 - 同步（低成本、快速响应）；代理内部按配置指纹重建真实模型，支持热更
     */
    @Bean
    public ChatModel reviewChatModel(SystemConfigService systemConfigService) {
        return new ConfigurableChatModel(systemConfigService);
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
        /** 单次调用超时；推理型模型长推理可能超过默认 60s */
        private Duration timeout = Duration.ofMinutes(5);
    }

    /**
     * jev 结构化决策模型配置属性（意图路由/生成方案路由加速）
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "ai.jev")
    public static class AiJevProperties {
        private Boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Integer timeoutSeconds;
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
