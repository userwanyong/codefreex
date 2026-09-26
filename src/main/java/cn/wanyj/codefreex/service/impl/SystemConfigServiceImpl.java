package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.model.dto.response.CreditConfigVO;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.SystemConfigMapper;
import cn.wanyj.codefreex.model.dto.response.SystemConfigGroupVO;
import cn.wanyj.codefreex.model.dto.response.SystemConfigItemVO;
import cn.wanyj.codefreex.model.entity.SystemConfig;
import cn.wanyj.codefreex.model.enums.SystemConfigKey;
import cn.wanyj.codefreex.service.SystemConfigService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.wanyj.codefreex.model.entity.table.SystemConfigTableDef.SYSTEM_CONFIG;

/**
 * 系统配置服务实现：
 * 启动时将 yml 中的既有配置（含 AI 服务商密钥）播种入库作为初始值，
 * 之后以库中值为准并维护进程内缓存，管理端更新后立即生效（单实例部署）。
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    /**
     * 敏感配置的展示掩码：任何接口都不回传明文，仅用于提示“已配置”
     */
    private static final String SENSITIVE_MASK = "********";

    private final SystemConfigMapper systemConfigMapper;
    private final AiConfig.AiModelProperties aiModelProperties;
    private final AiConfig.AiReviewProperties aiReviewProperties;
    private final AiConfig.AiJevProperties aiJevProperties;
    private final AppRuntimeConfig.WorkflowProperties workflowProperties;

    /**
     * 进程内配置缓存（键 -> 值）
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reloadAndSeed();
    }

    @Override
    public String getString(SystemConfigKey key) {
        String value = cache.get(key.getKey());
        if (value != null) {
            return value;
        }
        return resolveBootstrapDefault(key);
    }

    @Override
    public CreditConfigVO getCreditConfig() {
        CreditConfigVO vo = new CreditConfigVO();
        vo.setFirstGenerateCost(getInt(SystemConfigKey.CREDIT_FIRST_GENERATE_COST));
        vo.setChatRoundCost(getInt(SystemConfigKey.CREDIT_CHAT_ROUND_COST));
        vo.setInviteReward(getInt(SystemConfigKey.CREDIT_INVITE_REWARD));
        vo.setInviteCreateCostPerUse(getInt(SystemConfigKey.CREDIT_INVITE_CREATE_COST_PER_USE));
        vo.setDeployHourlyCost(getInt(SystemConfigKey.CREDIT_DEPLOY_HOURLY_COST));
        vo.setDeployBillingIntervalMinutes(Math.max(1, getInt(SystemConfigKey.CREDIT_DEPLOY_BILLING_INTERVAL_MINUTES)));
        return vo;
    }

    @Override
    public int getInt(SystemConfigKey key) {
        String value = getString(key);
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            log.warn("系统配置 {} 的值 '{}' 不是合法整数，回退默认值", key.getKey(), value);
            return Integer.parseInt(resolveBootstrapDefault(key).trim());
        }
    }

    @Override
    public double getDouble(SystemConfigKey key) {
        String value = getString(key);
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            log.warn("系统配置 {} 的值 '{}' 不是合法小数，回退默认值", key.getKey(), value);
            return Double.parseDouble(resolveBootstrapDefault(key).trim());
        }
    }

    @Override
    public boolean getBoolean(SystemConfigKey key) {
        String value = getString(key);
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        log.warn("系统配置 {} 的值 '{}' 不是合法布尔值，回退默认值", key.getKey(), value);
        return Boolean.parseBoolean(resolveBootstrapDefault(key).trim());
    }

    @Override
    public List<SystemConfigGroupVO> listGroupedConfigs() {
        Map<String, SystemConfigGroupVO> groups = new LinkedHashMap<>();
        for (SystemConfigKey key : SystemConfigKey.values()) {
            SystemConfigGroupVO group = groups.computeIfAbsent(key.getGroup(), g -> {
                SystemConfigGroupVO vo = new SystemConfigGroupVO();
                vo.setGroup(g);
                vo.setGroupName(key.getGroupName());
                vo.setItems(new ArrayList<>());
                return vo;
            });
            SystemConfigItemVO item = new SystemConfigItemVO();
            item.setKey(key.getKey());
            item.setLabel(key.getLabel());
            // 敏感配置永不回传明文，已配置时仅返回掩码
            String value = getString(key);
            item.setValue(key.isSensitive() ? maskSensitive(value) : value);
            item.setValueType(key.getValueType().name());
            item.setSensitive(key.isSensitive());
            item.setDescription(key.getDescription());
            item.setDefaultValue(key.isSensitive() ? null : resolveBootstrapDefault(key));
            group.getItems().add(item);
        }
        return new ArrayList<>(groups.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "配置项不能为空");
        }
        // 先整体校验，全部通过后再落库，避免部分更新
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            SystemConfigKey key = SystemConfigKey.fromKey(entry.getKey());
            if (key == null) {
                throw new BusinessException(ResponseCode.PARAMS_ERROR, "未知的配置项: " + entry.getKey());
            }
            // 敏感配置提交掩码或空串表示“不修改”，跳过校验与更新
            if (key.isSensitive() && isUnchangedSensitive(entry.getValue())) {
                continue;
            }
            validateValue(key, entry.getValue());
        }
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            SystemConfigKey key = SystemConfigKey.fromKey(entry.getKey());
            if (key != null && key.isSensitive() && isUnchangedSensitive(entry.getValue())) {
                continue;
            }
            upsert(entry.getKey(), entry.getValue());
            cache.put(entry.getKey(), entry.getValue());
        }
        log.info("系统配置已更新: {}", configs.keySet());
    }

    /**
     * 敏感值脱敏：非空返回固定掩码，未配置返回空串
     */
    private String maskSensitive(String value) {
        return value == null || value.isBlank() ? "" : SENSITIVE_MASK;
    }

    /**
     * 判断敏感配置的提交值是否表示“保持不变”（掩码原样回传或留空）
     */
    private boolean isUnchangedSensitive(String value) {
        return value == null || value.isBlank() || SENSITIVE_MASK.equals(value.trim());
    }

    /**
     * 加载库中配置并播种缺失键：优先取 yml 既有值（迁移历史配置），否则取出厂默认值
     */
    private void reloadAndSeed() {
        List<SystemConfig> rows = systemConfigMapper.selectListByQuery(
                QueryWrapper.create().where(SYSTEM_CONFIG.IS_DELETE.eq(0))
        );
        for (SystemConfig row : rows) {
            cache.put(row.getConfigKey(), row.getConfigValue());
        }
        for (SystemConfigKey key : SystemConfigKey.values()) {
            if (cache.containsKey(key.getKey())) {
                continue;
            }
            String value = resolveBootstrapDefault(key);
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key.getKey());
            config.setConfigValue(value);
            config.setRemark(key.getLabel());
            systemConfigMapper.insert(config);
            cache.put(key.getKey(), value);
            log.info("系统配置播种: {} = {}", key.getKey(), key.isSensitive() ? "******" : value);
        }
    }

    /**
     * 解析配置的初始默认值：yml 已配置的值优先（兼容存量部署），否则使用枚举出厂默认值
     */
    private String resolveBootstrapDefault(SystemConfigKey key) {
        String ymlValue = switch (key) {
            case AI_MAIN_API_KEY -> aiModelProperties.getApiKey();
            case AI_MAIN_BASE_URL -> aiModelProperties.getBaseUrl();
            case AI_MAIN_MODEL_NAME -> aiModelProperties.getModelName();
            case AI_MAIN_TEMPERATURE -> String.valueOf(aiModelProperties.getTemperature());
            case AI_MAIN_MAX_TOKENS -> String.valueOf(aiModelProperties.getMaxTokens());
            case AI_MAIN_LOG_REQUESTS -> String.valueOf(aiModelProperties.isLogRequests());
            case AI_MAIN_LOG_RESPONSES -> String.valueOf(aiModelProperties.isLogResponses());
            case AI_REVIEW_API_KEY -> aiReviewProperties.getApiKey();
            case AI_REVIEW_BASE_URL -> aiReviewProperties.getBaseUrl();
            case AI_REVIEW_MODEL_NAME -> aiReviewProperties.getModelName();
            case AI_REVIEW_TEMPERATURE -> String.valueOf(aiReviewProperties.getTemperature());
            case AI_REVIEW_MAX_TOKENS -> String.valueOf(aiReviewProperties.getMaxTokens());
            case AI_REVIEW_TIMEOUT_SECONDS -> String.valueOf(aiReviewProperties.getTimeout().toSeconds());
            case AI_JEV_ENABLED -> aiJevProperties.getEnabled() == null ? null : String.valueOf(aiJevProperties.getEnabled());
            case AI_JEV_API_KEY -> aiJevProperties.getApiKey();
            case AI_JEV_BASE_URL -> aiJevProperties.getBaseUrl();
            case AI_JEV_MODEL_NAME -> aiJevProperties.getModelName();
            case AI_JEV_TIMEOUT_SECONDS -> aiJevProperties.getTimeoutSeconds() == null
                    ? null : String.valueOf(aiJevProperties.getTimeoutSeconds());
            case AI_GALLERY_PEXELS_API_KEY -> workflowProperties.getPexelsApiKey();
            case AI_GALLERY_PIXABAY_API_KEY -> workflowProperties.getPixabayApiKey();
            default -> null;
        };
        if (ymlValue != null && !ymlValue.isBlank()) {
            return ymlValue;
        }
        return key.getDefaultValue();
    }

    private void validateValue(SystemConfigKey key, String value) {
        if (value == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "配置项 " + key.getLabel() + " 的值不能为空");
        }
        try {
            switch (key.getValueType()) {
                case INT -> {
                    int parsed = Integer.parseInt(value.trim());
                    if (parsed < 0) {
                        throw new NumberFormatException("negative");
                    }
                }
                case DOUBLE -> {
                    double parsed = Double.parseDouble(value.trim());
                    if (parsed < 0 || Double.isNaN(parsed)) {
                        throw new NumberFormatException("invalid");
                    }
                }
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                        throw new NumberFormatException("invalid boolean");
                    }
                }
                case STRING -> {
                    // 任意字符串
                }
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR,
                    "配置项 " + key.getLabel() + " 的值 '" + value + "' 不符合类型 " + key.getValueType());
        }
    }

    private void upsert(String key, String value) {
        boolean updated = UpdateChain.of(SystemConfig.class)
                .where(SYSTEM_CONFIG.CONFIG_KEY.eq(key))
                .and(SYSTEM_CONFIG.IS_DELETE.eq(0))
                .set(SYSTEM_CONFIG.CONFIG_VALUE, value)
                .update();
        if (!updated) {
            SystemConfigKey configKey = SystemConfigKey.fromKey(key);
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setRemark(configKey != null ? configKey.getLabel() : null);
            systemConfigMapper.insert(config);
        }
    }
}
