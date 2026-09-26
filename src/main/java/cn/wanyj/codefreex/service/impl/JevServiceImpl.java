package cn.wanyj.codefreex.service.impl;

import cn.hutool.http.HttpRequest;
import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.model.enums.SystemConfigKey;
import cn.wanyj.codefreex.service.JevService;
import cn.wanyj.codefreex.service.SystemConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * jev 结构化决策服务实现。
 *
 * 请求：POST {ai.jev.base-url}/systemone，Bearer 鉴权，body 为
 * {model, state, questions: {<question>: {type: choice, instructions, criteria}}}。
 * 响应（2026-09 实测）：{model, answers: {<question>: {type, choice, confidence, probabilities}},
 * usage: {input_tokens, output_tokens}}，解析路径为 answers.<question>.choice。
 *
 * 决策定义外置在 prompts 目录的 JSON 文件中（question/instructions/criteria/statePrefix），
 * 判据文案可随提示词一起调整而无需改代码；任何异常一律吞掉返回空，由调用方回退原链路。
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JevServiceImpl implements JevService {

    private final SystemConfigService systemConfigService;
    private final AiConfig.PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<String> classify(String content, String definitionFile) {
        if (!isReady()) {
            return Optional.empty();
        }
        long start = System.currentTimeMillis();
        try {
            JsonNode definition = objectMapper.readTree(promptLoader.load(definitionFile));
            String question = definition.path("question").asText("");
            if (question.isBlank()) {
                log.warn("[Jev] 决策定义缺少 question 字段, definition={}", definitionFile);
                return Optional.empty();
            }

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", systemConfigService.getString(SystemConfigKey.AI_JEV_MODEL_NAME));
            requestBody.put("state", definition.path("statePrefix").asText("") + content);
            ObjectNode questions = requestBody.putObject("questions");
            ObjectNode questionNode = questions.putObject(question);
            questionNode.put("type", "choice");
            questionNode.put("instructions", definition.path("instructions").asText());
            ObjectNode criteria = questionNode.putObject("criteria");
            JsonNode criteriaNode = definition.path("criteria");
            for (Iterator<Map.Entry<String, JsonNode>> it = criteriaNode.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                criteria.put(entry.getKey(), entry.getValue().asText());
            }

            String response = HttpRequest.post(resolveEndpointUrl())
                    .header("Authorization", "Bearer " + systemConfigService.getString(SystemConfigKey.AI_JEV_API_KEY))
                    .contentType("application/json;charset=utf-8")
                    .body(requestBody.toString())
                    .timeout(systemConfigService.getInt(SystemConfigKey.AI_JEV_TIMEOUT_SECONDS) * 1000)
                    .execute()
                    .body();
            JsonNode root = objectMapper.readTree(response);
            String choice = root.path("answers").path(question).path("choice").asText("");
            if (choice.isBlank()) {
                log.warn("[Jev] 响应中无 choice 结果, question={}, definition={}, body={}", question, definitionFile, response);
                return Optional.empty();
            }
            JsonNode usage = root.path("usage");
            log.info("[Jev] 决策完成, question={}, choice={}, confidence={}, 耗时={}ms, tokens={}/{}",
                    question, choice,
                    root.path("answers").path(question).path("confidence").asText(""),
                    System.currentTimeMillis() - start,
                    usage.path("input_tokens").asInt(0), usage.path("output_tokens").asInt(0));
            return Optional.of(choice.trim());
        } catch (Exception e) {
            log.warn("[Jev] 决策调用失败, 将回退预审核模型, definition={}, 耗时={}ms",
                    definitionFile, System.currentTimeMillis() - start, e);
            return Optional.empty();
        }
    }

    /**
     * 就绪条件：开关开启且 API Key 已配置（key 为空视为未启用，自动走原链路）
     */
    private boolean isReady() {
        return systemConfigService.getBoolean(SystemConfigKey.AI_JEV_ENABLED)
                && !systemConfigService.getString(SystemConfigKey.AI_JEV_API_KEY).isBlank();
    }

    /**
     * 拼接决策端点地址（容忍 base-url 结尾多带斜杠）
     */
    private String resolveEndpointUrl() {
        String baseUrl = systemConfigService.getString(SystemConfigKey.AI_JEV_BASE_URL);
        String normalized = baseUrl == null ? "" : baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/systemone";
    }
}
