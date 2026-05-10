package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.dto.response.PromptReviewResult;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.service.AiGenService;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 生成服务实现
 *
 * @author wanyj
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGenServiceImpl implements AiGenService {

    private final ApplicationContext applicationContext;
    private final ChatModel reviewChatModel;
    private final AiConfig.PromptLoader promptLoader;
    private final CodePersistStrategy htmlSingleFileStrategy;
    private final AppMapper appMapper;
    private final AppService appService;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;
    private final ObjectMapper objectMapper;

    /**
     * HTML 代码块提取正则
     */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.DOTALL);

    @Override
    public Flux<ServerSentEvent<String>> chatToGenCode(Long appId, String userMessage) {
        Long userId = UserContext.getLoginUserId();
        App app = checkAppAndOwner(appId, userId);

        // 2. 更新应用状态为生成中
        updateAppStatus(appId, AppStatus.GENERATING.getValue());

        String deployKey = app.getDeployKey();

        // 3. 获取系统提示词
        String systemPrompt = promptLoader.load("html_single.txt");
        ChatHistory userHistory = chatHistoryService.saveUserMessage(appId, userId, userMessage);
        var chatMemory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        chatMemory.add(UserMessage.from(userMessage));

        // 4. 从 Spring 容器获取多例 StreamingChatModel
        StreamingChatModel streamingModel = applicationContext.getBean(StreamingChatModel.class);

        return Flux.create(sink -> {
            StringBuilder fullResponse = new StringBuilder();

            List<ChatMessage> messages = new ArrayList<>();
            messages.addAll(chatMemory.messages());

            streamingModel.chat(messages, new dev.langchain4j.model.chat.response.StreamingChatResponseHandler() {

                @Override
                public void onPartialResponse(String partialToken) {
                    fullResponse.append(partialToken);
                    try {
                        String json = objectMapper.writeValueAsString(
                                new SseMessage("ai_response", partialToken));
                        sink.next(ServerSentEvent.<String>builder().data(json).build());
                    } catch (Exception e) {
                        log.error("SSE 消息序列化失败", e);
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    // 流式结束，保存代码并发送 done 事件
                    try {
                        String fullText = fullResponse.toString();
                        String code = extractHtmlCode(fullText);

                        if (code != null && !code.isEmpty()) {
                            htmlSingleFileStrategy.persist(deployKey, code);
                            updateAppStatus(appId, AppStatus.GENERATED.getValue());
                            log.info("应用 {} 代码生成完成", appId);
                        } else {
                            // AI 没有返回有效的代码块，保存完整响应
                            log.warn("应用 {} AI 未返回有效的 HTML 代码块，保存完整响应", appId);
                            htmlSingleFileStrategy.persist(deployKey, fullText);
                            updateAppStatus(appId, AppStatus.GENERATED.getValue());
                        }

                        chatHistoryService.saveAiMessage(appId, userId, fullText, userHistory.getId());
                        chatMemory.add(AiMessage.from(fullText));

                        String doneJson = objectMapper.writeValueAsString(new SseMessage("done", ""));
                        sink.next(ServerSentEvent.<String>builder().data(doneJson).build());
                    } catch (Exception e) {
                        log.error("保存生成代码失败: appId={}", appId, e);
                        updateAppStatus(appId, AppStatus.DRAFT.getValue());
                        String errorText = "【生成失败】" + e.getMessage();
                        try {
                            chatHistoryService.saveAiMessage(appId, userId, errorText, userHistory.getId());
                            chatMemory.add(AiMessage.from(errorText));
                        } catch (Exception historyError) {
                            log.error("保存失败消息到历史记录失败: appId={}", appId, historyError);
                        }
                        try {
                            String errorJson = objectMapper.writeValueAsString(
                                    new SseMessage("error", "代码保存失败: " + e.getMessage()));
                            sink.next(ServerSentEvent.<String>builder().data(errorJson).build());
                        } catch (Exception ex) {
                            log.error("错误消息序列化失败", ex);
                        }
                    } finally {
                        sink.complete();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    log.error("AI 流式生成失败: appId={}", appId, error);
                    updateAppStatus(appId, AppStatus.DRAFT.getValue());
                    String errorText = "【生成异常】" + error.getMessage();
                    try {
                        chatHistoryService.saveAiMessage(appId, userId, errorText, userHistory.getId());
                        chatMemory.add(AiMessage.from(errorText));
                    } catch (Exception historyError) {
                        log.error("保存异常消息到历史记录失败: appId={}", appId, historyError);
                    }
                    try {
                        String errorJson = objectMapper.writeValueAsString(
                                new SseMessage("error", "AI 生成失败: " + error.getMessage()));
                        sink.next(ServerSentEvent.<String>builder().data(errorJson).build());
                    } catch (Exception e) {
                        log.error("错误消息序列化失败", e);
                    }
                    sink.complete();
                }
            });
        });
    }

    @Override
    public PromptReviewResult reviewPrompt(String prompt) {
        String reviewPromptText = promptLoader.load("prompt_review.txt") + prompt;

        try {
            ChatResponse response = reviewChatModel.chat(dev.langchain4j.data.message.UserMessage.from(reviewPromptText));
            String resultText = response.aiMessage().text().trim();

            // 提取 JSON 部分
            String json = resultText;
            int jsonStart = resultText.indexOf('{');
            int jsonEnd = resultText.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = resultText.substring(jsonStart, jsonEnd + 1);
            }

            var node = objectMapper.readTree(json);
            boolean safe = node.get("safe").asBoolean();
            String route = node.has("route") ? node.get("route").asText() : "html";
            String reason = node.has("reason") ? node.get("reason").asText() : "";

            PromptReviewResult result = new PromptReviewResult();
            result.setSafe(safe);
            result.setRouteSuggestion(route);
            result.setReason(reason);
            return result;
        } catch (Exception e) {
            log.error("预审核失败，默认放行", e);
            // 预审核失败时默认放行，使用 html 模式
            return PromptReviewResult.safe("html");
        }
    }

    @Override
    public String optimizePrompt(String prompt) {
        String optimizePromptText = promptLoader.load("prompt_optimize.txt") + prompt;

        try {
            ChatResponse response = reviewChatModel.chat(dev.langchain4j.data.message.UserMessage.from(optimizePromptText));
            return response.aiMessage().text().trim();
        } catch (Exception e) {
            log.error("优化提示词失败，返回原始提示词", e);
            return prompt;
        }
    }

    /**
     * 从 AI 回复中提取 HTML 代码
     */
    private String extractHtmlCode(String aiResponse) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(aiResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 如果没有找到代码块标记，判断整体是否就是 HTML
        String trimmed = aiResponse.trim();
        if (trimmed.contains("<!DOCTYPE") || trimmed.contains("<html") || trimmed.contains("<div")) {
            return trimmed;
        }
        return null;
    }

    /**
     * 更新应用状态
     */
    private void updateAppStatus(Long appId, String status) {
        try {
            appService.updateAppStatus(appId, status);
        } catch (Exception e) {
            log.error("更新应用状态失败: appId={}, status={}", appId, status, e);
        }
    }

    private App checkAppAndOwner(Long appId, Long userId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (userId == null || !userId.equals(app.getUserId())) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权限操作该应用");
        }
        return app;
    }

    /**
     * SSE 消息封装
     */
    public record SseMessage(String type, String data) {
    }
}
