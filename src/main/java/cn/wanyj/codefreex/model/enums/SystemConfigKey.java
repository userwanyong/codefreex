package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 系统配置键定义：键的元信息（分组、类型、默认值、是否敏感）统一在此维护，
 * 管理端配置页与取值方均以本枚举为准；缺省值在应用启动时以 yml 值优先播种入库。
 *
 * @author wanyj
 */
@Getter
@AllArgsConstructor
public enum SystemConfigKey {

    // ================= AI 主生成模型（流式） =================
    AI_MAIN_API_KEY("ai.main.api-key", "ai", "AI 模型配置", "主生成模型 API Key", ValueType.STRING, "", true,
            "OpenAI 兼容接口的 API 密钥"),
    AI_MAIN_BASE_URL("ai.main.base-url", "ai", "AI 模型配置", "主生成模型接口地址", ValueType.STRING, "", false,
            "OpenAI 兼容接口地址，例如 https://api.openai.com/v1"),
    AI_MAIN_MODEL_NAME("ai.main.model-name", "ai", "AI 模型配置", "主生成模型名称", ValueType.STRING, "", false,
            "模型标识，例如 deepseek-chat"),
    AI_MAIN_TEMPERATURE("ai.main.temperature", "ai", "AI 模型配置", "主生成模型温度", ValueType.DOUBLE, "0.7", false,
            "取值 0-2，越高越发散"),
    AI_MAIN_MAX_TOKENS("ai.main.max-tokens", "ai", "AI 模型配置", "主生成模型最大输出 Token", ValueType.INT, "16384", false, null),
    AI_MAIN_LOG_REQUESTS("ai.main.log-requests", "ai", "AI 模型配置", "主生成模型记录请求日志", ValueType.BOOLEAN, "false", false, null),
    AI_MAIN_LOG_RESPONSES("ai.main.log-responses", "ai", "AI 模型配置", "主生成模型记录响应日志", ValueType.BOOLEAN, "false", false, null),

    // ================= AI 预审核模型（同步） =================
    AI_REVIEW_API_KEY("ai.review.api-key", "ai", "AI 模型配置", "预审核模型 API Key", ValueType.STRING, "", true,
            "提示词预审核与迭代修改所用模型的 API 密钥"),
    AI_REVIEW_BASE_URL("ai.review.base-url", "ai", "AI 模型配置", "预审核模型接口地址", ValueType.STRING, "", false, null),
    AI_REVIEW_MODEL_NAME("ai.review.model-name", "ai", "AI 模型配置", "预审核模型名称", ValueType.STRING, "", false, null),
    AI_REVIEW_TEMPERATURE("ai.review.temperature", "ai", "AI 模型配置", "预审核模型温度", ValueType.DOUBLE, "0.3", false, null),
    AI_REVIEW_MAX_TOKENS("ai.review.max-tokens", "ai", "AI 模型配置", "预审核模型最大输出 Token", ValueType.INT, "2048", false,
            "承担工具调用循环时不宜过小，避免输出前被截断"),
    AI_REVIEW_TIMEOUT_SECONDS("ai.review.timeout-seconds", "ai", "AI 模型配置", "预审核模型超时（秒）", ValueType.INT, "600", false,
            "推理型模型大上下文工具循环单次调用可超过 300s，过短会频繁超时重试"),

    // ================= AI 结构化决策模型（jev） =================
    AI_JEV_ENABLED("ai.jev.enabled", "ai", "AI 模型配置", "jev 决策模型开关", ValueType.BOOLEAN, "true", false,
            "启用后意图路由/生成方案路由优先走 jev 结构化决策模型（毫秒级），未启用或调用失败自动回退预审核模型"),
    AI_JEV_API_KEY("ai.jev.api-key", "ai", "AI 模型配置", "jev 决策模型 API Key", ValueType.STRING, "", true,
            "jev 结构化决策模型的 API 密钥，留空视为未配置，相关判断自动回退预审核模型"),
    AI_JEV_BASE_URL("ai.jev.base-url", "ai", "AI 模型配置", "jev 决策模型接口地址", ValueType.STRING,
            "https://www.portixapi.com/v1", false, null),
    AI_JEV_MODEL_NAME("ai.jev.model-name", "ai", "AI 模型配置", "jev 决策模型名称", ValueType.STRING, "jev-1.13.0", false, null),
    AI_JEV_TIMEOUT_SECONDS("ai.jev.timeout-seconds", "ai", "AI 模型配置", "jev 决策模型超时（秒）", ValueType.INT, "10", false,
            "jev 单次决策通常 70-500ms，超时后自动回退预审核模型"),

    // ================= AI 图库服务 =================
    AI_GALLERY_PEXELS_API_KEY("ai.gallery.pexels-api-key", "ai", "AI 模型配置", "Pexels 图库 API Key", ValueType.STRING, "", true,
            "工作流配图素材来源，留空则使用占位图"),
    AI_GALLERY_PIXABAY_API_KEY("ai.gallery.pixabay-api-key", "ai", "AI 模型配置", "Pixabay 图库 API Key", ValueType.STRING, "", true,
            "工作流插图素材来源，留空则使用占位图"),

    // ================= 码点计费 =================
    CREDIT_FIRST_GENERATE_COST("credit.first-generate-cost", "credit", "码点计费配置", "创建应用消耗码点", ValueType.INT, "50", false,
            "创建应用（首次生成）时一次性扣减"),
    CREDIT_CHAT_ROUND_COST("credit.chat-round-cost", "credit", "码点计费配置", "每轮对话消耗码点", ValueType.INT, "10", false,
            "非首次生成的每轮对话扣减"),
    CREDIT_INVITE_REWARD("credit.invite-reward", "credit", "码点计费配置", "邀请注册奖励码点", ValueType.INT, "100", false,
            "通过邀请码注册后被邀请人初始码点与邀请人奖励码点"),
    CREDIT_INVITE_CREATE_COST_PER_USE("credit.invite-create-cost-per-use", "credit", "码点计费配置", "创建邀请码单次消耗码点", ValueType.INT, "50", false,
            "普通用户创建邀请码时按最大可用次数乘以该值扣减"),
    CREDIT_DEPLOY_HOURLY_COST("credit.deploy-hourly-cost", "credit", "码点计费配置", "部署每周期消耗码点", ValueType.INT, "10", false,
            "应用处于已部署状态时每个计费周期扣减，置 0 关闭部署计费"),
    CREDIT_DEPLOY_BILLING_INTERVAL_MINUTES("credit.deploy-billing-interval-minutes", "credit", "码点计费配置", "部署计费周期（分钟）", ValueType.INT, "60", false,
            "默认 60 即按小时计费；码点不足以支付下一周期时自动取消部署"),

    ;

    /**
     * 配置键（入库唯一标识）
     */
    private final String key;
    /**
     * 分组编码
     */
    private final String group;
    /**
     * 分组名称（管理页展示）
     */
    private final String groupName;
    /**
     * 配置项名称（管理页展示）
     */
    private final String label;
    /**
     * 值类型
     */
    private final ValueType valueType;
    /**
     * 出厂默认值（yml 优先播种，此值兜底）
     */
    private final String defaultValue;
    /**
     * 是否敏感信息（密钥类）
     */
    private final boolean sensitive;
    /**
     * 说明
     */
    private final String description;

    /**
     * 按键反查枚举
     */
    public static SystemConfigKey fromKey(String key) {
        return Arrays.stream(values())
                .filter(k -> k.key.equals(key))
                .findFirst()
                .orElse(null);
    }

    /**
     * 配置值类型
     */
    public enum ValueType {
        /** 任意字符串 */
        STRING,
        /** 整数 */
        INT,
        /** 小数 */
        DOUBLE,
        /** 布尔（true/false） */
        BOOLEAN
    }
}
