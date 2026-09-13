package cn.wanyj.codefreex.config;

import cn.wanyj.codefreex.model.enums.SystemConfigKey;
import cn.wanyj.codefreex.service.SystemConfigService;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 可热更新的同步聊天模型代理：对外是单例 Bean，内部按当前系统配置缓存真实模型，
 * 管理端修改配置后无需重启即对下一次调用生效。
 *
 * @author wanyj
 */
public class ConfigurableChatModel implements ChatModel {

    private final SystemConfigService systemConfigService;

    private final ReentrantLock rebuildLock = new ReentrantLock();

    private volatile OpenAiChatModel delegate;

    private volatile String delegateFingerprint;

    public ConfigurableChatModel(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return current().doChat(chatRequest);
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return current().chat(chatRequest);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return current().defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return current().listeners();
    }

    @Override
    public ModelProvider provider() {
        return current().provider();
    }

    /**
     * 获取当前配置对应的模型实例；配置指纹变化时重建（双检锁，避免每次调用都构建）
     */
    private OpenAiChatModel current() {
        String fingerprint = buildFingerprint();
        OpenAiChatModel model = delegate;
        if (model != null && fingerprint.equals(delegateFingerprint)) {
            return model;
        }
        rebuildLock.lock();
        try {
            if (delegate == null || !fingerprint.equals(delegateFingerprint)) {
                delegate = buildModel();
                delegateFingerprint = fingerprint;
            }
            return delegate;
        } finally {
            rebuildLock.unlock();
        }
    }

    private OpenAiChatModel buildModel() {
        return OpenAiChatModel.builder()
                .apiKey(systemConfigService.getString(SystemConfigKey.AI_REVIEW_API_KEY))
                .baseUrl(systemConfigService.getString(SystemConfigKey.AI_REVIEW_BASE_URL))
                .modelName(systemConfigService.getString(SystemConfigKey.AI_REVIEW_MODEL_NAME))
                .temperature(systemConfigService.getDouble(SystemConfigKey.AI_REVIEW_TEMPERATURE))
                .maxTokens(systemConfigService.getInt(SystemConfigKey.AI_REVIEW_MAX_TOKENS))
                .timeout(Duration.ofSeconds(systemConfigService.getInt(SystemConfigKey.AI_REVIEW_TIMEOUT_SECONDS)))
                .build();
    }

    /**
     * 参与重建判断的全部配置项的指纹
     */
    private String buildFingerprint() {
        return String.join("|",
                systemConfigService.getString(SystemConfigKey.AI_REVIEW_API_KEY),
                systemConfigService.getString(SystemConfigKey.AI_REVIEW_BASE_URL),
                systemConfigService.getString(SystemConfigKey.AI_REVIEW_MODEL_NAME),
                String.valueOf(systemConfigService.getDouble(SystemConfigKey.AI_REVIEW_TEMPERATURE)),
                String.valueOf(systemConfigService.getInt(SystemConfigKey.AI_REVIEW_MAX_TOKENS)),
                String.valueOf(systemConfigService.getInt(SystemConfigKey.AI_REVIEW_TIMEOUT_SECONDS)));
    }
}
