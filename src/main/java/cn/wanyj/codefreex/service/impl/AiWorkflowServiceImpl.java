package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.dto.response.WorkflowEvent;
import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;
import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.*;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import cn.wanyj.codefreex.service.tools.WorkflowFileTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流
 *
 * @author BanXia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiWorkflowServiceImpl implements AiWorkflowService {

    private static final Pattern HTML_CODE_PATTERN =
            Pattern.compile("```html\\s*\\R([\\s\\S]*?)```", Pattern.DOTALL);
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "rm -rf", "ddos", "sqlmap", "xss payload", "webshell",
            "ransomware", "trojan", "backdoor", "credential stuffing");

    private static final java.util.Set<String> VOID_TAGS = java.util.Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr");
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("^<([a-zA-Z][a-zA-Z0-9]*)");

    private static final String NODE_PROMPT_GUARD = "promptGuardNode";
    private static final String NODE_PROMPT_REVIEW = "promptReviewNode";
    private static final String NODE_PRD_GEN = "prdGenNode";
    private static final String NODE_IMAGE_PLAN = "imagePlanNode";
    private static final String NODE_IMAGE_FETCH = "imageFetchNode";
    private static final String NODE_PROMPT_ENHANCE = "promptEnhanceNode";
    private static final String NODE_ROUTE = "routeNode";
    private static final String NODE_CODE_GEN = "codeGenNode";
    private static final String NODE_QUALITY_CHECK = "qualityCheckNode";
    private static final String NODE_PERSIST = "persistNode";
    private static final String NODE_INTENT_CLASSIFY = "intentClassifyNode";
    private static final String NODE_CHAT_DIRECT = "chatDirectNode";
    private static final String NODE_VISUAL_EDIT = "visualEditNode";
    private static final String NODE_EDIT_COMPLETE = "editCompleteNode";
    private static final String NODE_CODE_FIX = "codeFixNode";
    private static final String NODE_FAIL = "failNode";

    private static final Map<String, Channel<?>> WORKFLOW_SCHEMA = Map.of(
            "retryCount", Channel.of(() -> 0),
            "imageAssets", Channel.of(ArrayList::new)
    );

    private final ApplicationContext applicationContext;
    private final ChatModel reviewChatModel;
    private final AiConfig.PromptLoader promptLoader;
    private final AppMapper appMapper;
    private final AppService appService;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;
    private final WorkflowImageService workflowImageService;
    private final CodePersistStrategyRegistry codePersistStrategyRegistry;
    private final WorkflowFileToolService workflowFileToolService;
    private final ObjectMapper objectMapper;
    private final AppRuntimeConfig.WorkflowProperties workflowProperties;

    private final Map<Long, WorkflowStatusResponse> statusMap = new ConcurrentHashMap<>();

    @Override
    public Flux<ServerSentEvent<String>> generate(Long appId, String message) {
        Long userId = UserContext.getLoginUserId();
        App app = checkAppAndOwner(appId, userId);
        log.info("[{}] ========= 工作流启动, appId={}, userId={}, 消息长度={} =========", "Workflow", appId, userId, message.length());

        return Flux.<ServerSentEvent<String>>create(sink -> {
            ChatHistory userHistory = null;
            try {
                appService.updateAppStatus(appId, AppStatus.GENERATING.getValue());
                userHistory = chatHistoryService.saveUserMessage(appId, userId, message);
                updateStatus(appId, "running", NODE_PROMPT_GUARD, app.getCodeGenType(), 0, "workflow started");

                CompiledGraph<WorkflowGraphState> graph = buildWorkflowGraph(appId, userId, app, message, userHistory, sink);
                graph.setMaxIterations(25);

                String threadId = "workflow-" + appId + "-" + System.nanoTime();
                RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();

                WorkflowGraphState finalState = null;
                for (NodeOutput<WorkflowGraphState> output : graph.stream(Map.of("message", message), runnableConfig)) {
                    finalState = output.state();
                    updateStatus(
                            appId,
                            "running",
                            output.node(),
                            finalState.route(),
                            finalState.retryCount(),
                            finalState.statusMessage());
                }

                if (finalState == null) {
                    throw new RuntimeException("Workflow produced no state");
                }

                if (finalState.blocked() || !finalState.reviewSafe()) {
                    String errorMessage = finalState.errorMessageOr("Prompt rejected");
                    log.warn("[{}] 工作流被拦截, appId={}, 原因={}", "Workflow", appId, errorMessage);
                    persistFailure(appId, userId, userHistory, errorMessage);
                    appService.updateAppStatus(appId, AppStatus.DRAFT.getValue());
                    updateStatus(appId, "blocked", finalState.currentNode(), finalState.route(),
                            finalState.retryCount(), errorMessage);
                    emitError(sink, errorMessage);
                    emitDone(sink);
                    sink.complete();
                    return;
                }

                // 直接对话路径 - 普通对话
                if (!finalState.isCodingTask() && !finalState.isVisualEdit()) {
                    String chatResponse = finalState.chatResponse();
                    if (chatResponse != null && !chatResponse.isBlank()) {
                        ChatMemory chatMemory = chatMemoryService.getChatMemory(
                                appId, userId, promptLoader.load("workflow_chat.txt"));
                        chatMemory.add(AiMessage.from(chatResponse));
                    }
                    appService.updateAppStatus(appId, AppStatus.GENERATED.getValue());
                    log.info("[{}] ========= 直接对话完成, appId={} =========", "Workflow", appId);
                    updateStatus(appId, "completed", NODE_CHAT_DIRECT, app.getCodeGenType(),
                            finalState.retryCount(), "chat completed");
                    emitDone(sink);
                    sink.complete();
                    return;
                }

                // 编码任务 & 可视化编辑 - 统一质检流程
                if (!finalState.qualityPass()) {
                    throw new RuntimeException(finalState.errorMessageOr("Quality check failed"));
                }

                String generatedContent = finalState.generatedContent();
                if (finalState.isVisualEdit()) {
                    ChatMemory chatMemory = chatMemoryService.getChatMemory(
                            appId, userId, promptLoader.load("workflow_visual_edit.txt"));
                    chatMemory.add(AiMessage.from("可视化编辑已完成，修改已保存到文件系统"));
                    appService.updateAppStatus(appId, AppStatus.GENERATED.getValue());
                    saveNodeMessage(appId, userId, userHistory.getId(), NODE_EDIT_COMPLETE, "edit completed");
                    log.info("[{}] ========= 可视化编辑完成, appId={}, 重试次数={} =========", "Workflow", appId, finalState.retryCount());
                    updateStatus(appId, "completed", NODE_VISUAL_EDIT, app.getCodeGenType(),
                            finalState.retryCount(), "visual edit completed");
                } else {
                    ChatMemory chatMemory = chatMemoryService.getChatMemory(
                            appId, userId, promptLoader.load(resolvePromptTemplate(finalState.routeType())));
                    chatMemory.add(AiMessage.from("代码已生成并保存到文件系统"));
                    appService.updateAppStatus(appId, AppStatus.GENERATED.getValue());
                    saveNodeMessage(appId, userId, userHistory.getId(), NODE_PERSIST, "artifacts persisted");
                    log.info("[{}] ========= 工作流完成, appId={}, route={}, 重试次数={} =========", "Workflow", appId, finalState.route(), finalState.retryCount());
                    updateStatus(appId, "completed", NODE_PERSIST, finalState.route(),
                            finalState.retryCount(), "workflow completed");
                }

                emitDone(sink);
                sink.complete();
            } catch (Exception e) {
                log.error("workflow failed, appId={}", appId, e);
                appService.updateAppStatus(appId, AppStatus.DRAFT.getValue());
                updateStatus(appId, "failed", "error", app.getCodeGenType(), 0, e.getMessage());
                if (userHistory != null) {
                    persistFailure(appId, userId, userHistory, "Workflow failed: " + e.getMessage());
                }
                if (!sink.isCancelled()) {
                    try {
                        emitError(sink, e.getMessage());
                        emitDone(sink);
                    } catch (Exception ignored) {
                    }
                    sink.complete();
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public WorkflowStatusResponse getStatus(Long appId) {
        return statusMap.computeIfAbsent(appId, id -> {
            WorkflowStatusResponse response = new WorkflowStatusResponse();
            response.setAppId(id);
            response.setStatus("idle");
            response.setCurrentNode("none");
            response.setRetryCount(0);
            response.setUpdateTime(LocalDateTime.now());
            return response;
        });
    }

    private CompiledGraph<WorkflowGraphState> buildWorkflowGraph(Long appId, Long userId, App app, String message,
                                                                 ChatHistory userHistory,
                                                                 FluxSink<ServerSentEvent<String>> sink) throws Exception {
        StateGraph<WorkflowGraphState> graph = new StateGraph<>(WORKFLOW_SCHEMA, WorkflowGraphState::new);

        graph.addNode(NODE_PROMPT_GUARD, state -> CompletableFuture.completedFuture(runPromptGuardNode(state, message, sink)));
        graph.addNode(NODE_PROMPT_REVIEW, state -> CompletableFuture.completedFuture(runPromptReviewNode(state, message, userHistory, userId, sink)));
        graph.addNode(NODE_PRD_GEN, state -> CompletableFuture.completedFuture(runPrdGenNode(state, app, message, userHistory, userId, sink)));
        graph.addNode(NODE_IMAGE_PLAN, state -> CompletableFuture.completedFuture(runImagePlanNode(state, message, userHistory, userId, sink)));
        graph.addNode(NODE_IMAGE_FETCH, state -> CompletableFuture.completedFuture(runImageFetchNode(state, message, userHistory, userId, sink)));
        graph.addNode(NODE_PROMPT_ENHANCE, state -> CompletableFuture.completedFuture(runPromptEnhanceNode(state, app, message, userHistory, userId, sink)));
        graph.addNode(NODE_ROUTE, state -> CompletableFuture.completedFuture(runRouteNode(state, app, message, userHistory, userId, sink)));
        graph.addNode(NODE_CODE_GEN, state -> CompletableFuture.completedFuture(runCodeGenNode(state, app, userId, userHistory, sink)));
        graph.addNode(NODE_QUALITY_CHECK, state -> CompletableFuture.completedFuture(runQualityCheckNode(state, userHistory, userId, sink)));
        graph.addNode(NODE_CODE_FIX, state -> CompletableFuture.completedFuture(runCodeFixNode(state, app, userId, userHistory, sink)));
        graph.addNode(NODE_INTENT_CLASSIFY, state -> CompletableFuture.completedFuture(runIntentClassifyNode(state, message, userHistory, userId, sink)));
        graph.addNode(NODE_CHAT_DIRECT, state -> CompletableFuture.completedFuture(runChatDirectNode(state, appId, userId, message, userHistory, sink)));
        graph.addNode(NODE_VISUAL_EDIT, state -> CompletableFuture.completedFuture(runVisualEditNode(state, app, userId, message, userHistory, sink)));
        graph.addNode(NODE_FAIL, state -> CompletableFuture.completedFuture(runFailNode(state, sink)));

        graph.addEdge(StateGraph.START, NODE_PROMPT_GUARD);
        graph.addConditionalEdges(NODE_PROMPT_GUARD,
                state -> CompletableFuture.completedFuture(state.blocked() ? "blocked" : "passed"),
                Map.of("blocked", StateGraph.END, "passed", NODE_PROMPT_REVIEW));
        graph.addConditionalEdges(NODE_PROMPT_REVIEW,
                state -> CompletableFuture.completedFuture(state.reviewSafe() ? "passed" : "rejected"),
                Map.of("passed", NODE_INTENT_CLASSIFY, "rejected", StateGraph.END));
        graph.addConditionalEdges(NODE_INTENT_CLASSIFY,
                state -> CompletableFuture.completedFuture(resolveIntentEdge(state)),
                Map.of("coding", NODE_PRD_GEN, "visual_edit", NODE_VISUAL_EDIT, "chat", NODE_CHAT_DIRECT));
        graph.addEdge(NODE_CHAT_DIRECT, StateGraph.END);
        graph.addEdge(NODE_PRD_GEN, NODE_ROUTE);
        graph.addEdge(NODE_ROUTE, NODE_IMAGE_PLAN);
        graph.addEdge(NODE_IMAGE_PLAN, NODE_IMAGE_FETCH);
        graph.addEdge(NODE_IMAGE_FETCH, NODE_PROMPT_ENHANCE);
        graph.addEdge(NODE_PROMPT_ENHANCE, NODE_CODE_GEN);
        graph.addEdge(NODE_CODE_GEN, NODE_QUALITY_CHECK);
        graph.addEdge(NODE_VISUAL_EDIT, NODE_QUALITY_CHECK);
        graph.addConditionalEdges(NODE_QUALITY_CHECK,
                state -> CompletableFuture.completedFuture(resolveQualityEdge(state)),
                Map.of("fix", NODE_CODE_FIX, "pass", StateGraph.END, "failed", NODE_FAIL));
        graph.addEdge(NODE_CODE_FIX, NODE_QUALITY_CHECK);
        graph.addEdge(NODE_FAIL, StateGraph.END);

        return graph.compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());
    }

    private Map<String, Object> runPromptGuardNode(WorkflowGraphState state, String message,
                                                   FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> promptGuardNode 开始执行, 消息长度={}", "Workflow", message.length());
        emitToolRequest(sink, NODE_PROMPT_GUARD, Map.of("message", message));
        String lower = message.toLowerCase();
        for (String keyword : BLOCKED_KEYWORDS) {
            if (lower.contains(keyword)) {
                log.warn("[{}] <<< promptGuardNode 拦截高风险关键词: {}", "Workflow", keyword);
                emitToolExecuted(sink, NODE_PROMPT_GUARD, Map.of("result", "blocked", "keyword", keyword));
                return Map.of(
                        "blocked", true,
                        "currentNode", NODE_PROMPT_GUARD,
                        "statusMessage", "prompt blocked",
                        "errorMessage", "High-risk prompt blocked");
            }
        }
        log.info("[{}] <<< promptGuardNode 通过安全检查", "Workflow");
        emitToolExecuted(sink, NODE_PROMPT_GUARD, Map.of("result", "pass"));
        return Map.of(
                "blocked", false,
                "currentNode", NODE_PROMPT_GUARD,
                "statusMessage", "prompt guard passed");
    }

    private Map<String, Object> runPromptReviewNode(WorkflowGraphState state, String message,
                                                    ChatHistory userHistory, Long userId,
                                                    FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> promptReviewNode 开始执行, appId={}", "Workflow", userHistory.getAppId());
        emitToolRequest(sink, NODE_PROMPT_REVIEW, Map.of("message", message));
        ReviewResult reviewResult = reviewPrompt(message);
        if (reviewResult.safe()) {
            log.info("[{}] <<< promptReviewNode 审核通过, appId={}", "Workflow", userHistory.getAppId());
        } else {
            log.warn("[{}] <<< promptReviewNode 审核拒绝, appId={}, reason={}", "Workflow", userHistory.getAppId(), reviewResult.reason());
        }
        emitToolExecuted(sink, NODE_PROMPT_REVIEW, reviewResult,
                reviewResult.safe() ? "Prompt review passed" : "Prompt review rejected: " + reviewResult.reason());
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_PROMPT_REVIEW,
                reviewResult.safe() ? "Prompt review passed" : "Prompt review rejected: " + reviewResult.reason());
        return Map.of(
                "reviewSafe", reviewResult.safe(),
                "currentNode", NODE_PROMPT_REVIEW,
                "statusMessage", reviewResult.safe() ? "prompt review passed" : "prompt review rejected",
                "errorMessage", reviewResult.safe() ? "" : "Prompt review rejected: " + reviewResult.reason());
    }

    private Map<String, Object> runIntentClassifyNode(WorkflowGraphState state, String message,
                                                      ChatHistory userHistory, Long userId,
                                                      FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> intentClassifyNode 开始执行, appId={}", "Workflow", userHistory.getAppId());
        emitToolRequest(sink, NODE_INTENT_CLASSIFY, Map.of("message", message));
        String intent = classifyIntent(message);
        String label = switch (intent) {
            case "visual_edit" -> "可视化编辑";
            case "chat" -> "普通对话";
            default -> "编码任务";
        };
        log.info("[{}] <<< intentClassifyNode 完成, appId={}, 意图={}", "Workflow", userHistory.getAppId(), label);
        emitToolExecuted(sink, NODE_INTENT_CLASSIFY, Map.of("intent", label), label);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_INTENT_CLASSIFY, label);
        return Map.of(
                "intent", intent,
                "currentNode", NODE_INTENT_CLASSIFY,
                "statusMessage", "intent classified: " + label);
    }

    private String classifyIntent(String message) {
        try {
            String prompt = promptLoader.load("workflow_intent.txt") + message;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            JsonNode node = objectMapper.readTree(extractJson(response.aiMessage().text().trim()));
            String intent = node.path("intent").asText("coding");
            if (List.of("coding", "visual_edit", "chat").contains(intent)) {
                return intent;
            }
            return "coding";
        } catch (Exception e) {
            log.warn("Intent classification failed, fallback to coding task", e);
            return "coding";
        }
    }

    private Map<String, Object> runChatDirectNode(WorkflowGraphState state, Long appId, Long userId,
                                                  String message, ChatHistory userHistory,
                                                  FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> chatDirectNode 开始执行, appId={}", "Workflow", appId);
        emitToolRequest(sink, NODE_CHAT_DIRECT, Map.of("message", message));

        String systemPrompt = promptLoader.load("workflow_chat.txt");
        ChatMemory chatMemory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        chatMemory.add(UserMessage.from(message));

        StreamingChatModel streamingModel = applicationContext.getBean(StreamingChatModel.class);
        List<ChatMessage> messages = new ArrayList<>(chatMemory.messages());
        StringBuilder responseBuilder = new StringBuilder();
        RuntimeException[] errorHolder = new RuntimeException[1];
        CountDownLatch latch = new CountDownLatch(1);

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
                emitAiResponse(sink, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorHolder[0] = new RuntimeException(error);
                latch.countDown();
            }
        });

        try {
            if (!latch.await(2, TimeUnit.MINUTES)) {
                log.error("[{}] 直接对话流式超时(2分钟), appId={}", "Workflow", appId);
                throw new RuntimeException("Direct chat stream timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Direct chat stream interrupted", e);
        }
        if (errorHolder[0] != null) {
            throw errorHolder[0];
        }

        String chatResponse = responseBuilder.toString();
        log.info("[{}] <<< chatDirectNode 完成, appId={}, 响应长度={}", "Workflow", appId, chatResponse.length());
        emitToolExecuted(sink, NODE_CHAT_DIRECT, Map.of("length", chatResponse.length()));
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_CHAT_DIRECT, chatResponse);
        return Map.of(
                "intent", "chat",
                "chatResponse", chatResponse,
                "currentNode", NODE_CHAT_DIRECT,
                "statusMessage", "chat completed");
    }

    private Map<String, Object> runVisualEditNode(WorkflowGraphState state, App app, Long userId,
                                                  String message, ChatHistory userHistory,
                                                  FluxSink<ServerSentEvent<String>> sink) {
        Long appId = app.getId();
        log.info("[{}] >>> visualEditNode 开始执行, appId={}", "Workflow", appId);
        emitToolRequest(sink, NODE_VISUAL_EDIT, Map.of("message", message));

        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getDeployKey());
        WorkflowFileTools fileTools = new WorkflowFileTools(rootDir, workflowFileToolService);
        List<ToolSpecification> toolSpecs = ToolSpecifications.toolSpecificationsFrom(fileTools);

        // 构建 system prompt
        String systemPrompt = promptLoader.load("workflow_visual_edit.txt");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from("请根据用户的编辑指令修改项目代码。先列出文件结构，再读取需要修改的文件，然后进行修改。\n\n用户编辑指令:\n" + message));

        // tool-calling 循环，最多 15 轮
        int maxRounds = 15;
        String editSummary = "";
        for (int round = 1; round < maxRounds; round++) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse chatResponse = reviewChatModel.chat(chatRequest);
            AiMessage aiMessage = chatResponse.aiMessage();
            messages.add(aiMessage);

            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    log.info("[{}] visualEditNode 执行工具: {} (round={})", "Workflow", toolRequest.name(), round);
                    String toolResult = executeToolCall(fileTools, toolRequest);
                    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
                }
            } else {
                editSummary = aiMessage.text();
                break;
            }
        }

        // 读取修改后的主文件内容作为 generatedContent（供质检节点使用）
        String generatedContent = readMainFileContent(rootDir);

        log.info("[{}] <<< visualEditNode 完成, appId={}, 编辑摘要长度={}", "Workflow", appId, editSummary.length());
        emitToolExecuted(sink, NODE_VISUAL_EDIT, Map.of("updated", true));
        saveNodeMessage(appId, userId, userHistory.getId(), NODE_VISUAL_EDIT, "可视化编辑完成");

        return Map.of(
                "intent", "visual_edit",
                "generatedContent", generatedContent,
                "currentNode", NODE_VISUAL_EDIT,
                "statusMessage", "visual edit completed");
    }

    /**
     * 读取项目主文件内容（index.html），用于质检节点。
     * 多文件项目拼接所有文件内容。
     */
    private String readMainFileContent(Path rootDir) {
        List<String> filePaths = workflowFileToolService.listFiles(rootDir);
        if (filePaths.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String fp : filePaths) {
            String content = workflowFileToolService.readFile(rootDir, fp);
            if (content != null && !content.isBlank()) {
                sb.append(content);
            }
        }
        return sb.toString();
    }

    private Map<String, Object> runPrdGenNode(WorkflowGraphState state, App app, String message,
                                              ChatHistory userHistory, Long userId,
                                              FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> prdGenNode 开始执行, appId={}, appName={}", "Workflow", app.getId(), app.getAppName());
        emitToolRequest(sink, NODE_PRD_GEN, Map.of("appId", app.getId()));
        String prd = generatePrd(app, message);
        log.info("[{}] <<< prdGenNode 完成, appId={}, PRD长度={}", "Workflow", app.getId(), prd.length());
        emitToolExecuted(sink, NODE_PRD_GEN, Map.of("prd", prd), prd);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_PRD_GEN, prd);
        return Map.of(
                "prd", prd,
                "currentNode", NODE_PRD_GEN,
                "statusMessage", "prd generated");
    }

    private Map<String, Object> runImagePlanNode(WorkflowGraphState state, String message,
                                                 ChatHistory userHistory, Long userId,
                                                 FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> imagePlanNode 开始执行, appId={}", "Workflow", userHistory.getAppId());
        List<String> imagePlan = List.of("content", "illustration", "diagram", "logo");
        try {
            String prompt = promptLoader.load("workflow_image_plan.txt") + message + "\n\nPRD:\n" + state.prd();
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            JsonNode node = objectMapper.readTree(extractJson(response.aiMessage().text().trim()));
            JsonNode items = node.path("items");
            if (items.isArray() && !items.isEmpty()) {
                List<String> planFromAi = new ArrayList<>();
                for (JsonNode item : items) {
                    String type = item.path("type").asText("");
                    if (!type.isBlank()) {
                        planFromAi.add(type);
                    }
                }
                if (!planFromAi.isEmpty()) {
                    imagePlan = planFromAi;
                }
            }
        } catch (Exception e) {
            log.warn("AI image plan failed, fallback to default", e);
        }
        emitToolRequest(sink, NODE_IMAGE_PLAN, Map.of("plan", imagePlan));
        emitToolExecuted(sink, NODE_IMAGE_PLAN, imagePlan, String.join(", ", imagePlan));
        log.info("[{}] <<< imagePlanNode 完成, appId={}, 图片计划={}", "Workflow", userHistory.getAppId(), imagePlan);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_IMAGE_PLAN,
                String.join(", ", imagePlan));
        return Map.of(
                "imagePlan", imagePlan,
                "currentNode", NODE_IMAGE_PLAN,
                "statusMessage", "image plan ready");
    }

    private Map<String, Object> runImageFetchNode(WorkflowGraphState state, String message,
                                                  ChatHistory userHistory, Long userId,
                                                  FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> imageFetchNode 开始执行, appId={}, 图片计划={}", "Workflow", userHistory.getAppId(), state.imagePlan());
        emitToolRequest(sink, NODE_IMAGE_FETCH, state.imagePlan());
        List<WorkflowImageAsset> assets;
        try {
            String planJson = objectMapper.writeValueAsString(
                    Map.of("plan", state.imagePlan(), "message", message));
            String prompt = promptLoader.load("workflow_image_fetch.txt") + planJson;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            JsonNode node = objectMapper.readTree(extractJson(response.aiMessage().text().trim()));
            JsonNode assetsNode = node.path("assets");
            if (assetsNode.isArray() && !assetsNode.isEmpty()) {
                // 并行获取各类型图片
                List<CompletableFuture<WorkflowImageAsset>> futures = new ArrayList<>();
                for (JsonNode assetNode : assetsNode) {
                    String type = assetNode.path("type").asText("");
                    String keyword = assetNode.path("keyword").asText("");
                    String mermaidCode = assetNode.path("mermaidCode").asText("");
                    String description = assetNode.path("description").asText("");
                    if (!type.isBlank() && !keyword.isBlank()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                workflowImageService.fetchSingleAsset(type, keyword, mermaidCode, description)));
                    }
                }
                if (!futures.isEmpty()) {
                    List<WorkflowImageAsset> aiAssets = futures.stream()
                            .map(CompletableFuture::join)
                            .toList();
                    assets = aiAssets;
                    log.info("[{}] <<< imageFetchNode 完成, appId={}, 资源数量={}", "Workflow", userHistory.getAppId(), assets.size());
                    String assetsMsg = assets.stream().map(a -> a.getType() + ": " + a.getUrl())
                            .reduce((a, b) -> a + ", " + b).orElse("no assets");
                    emitToolExecuted(sink, NODE_IMAGE_FETCH, assets, assetsMsg);
                    saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_IMAGE_FETCH, assetsMsg);
                    return Map.of(
                            "imageAssets", assets,
                            "currentNode", NODE_IMAGE_FETCH,
                            "statusMessage", "image assets collected");
                }
            }
        } catch (Exception e) {
            log.warn("AI image fetch failed, fallback to default", e);
        }
        assets = workflowImageService.collectAssets(message);
        log.info("[{}] <<< imageFetchNode 完成(兜底), appId={}, 资源数量={}", "Workflow", userHistory.getAppId(), assets.size());
        String defaultAssetsMsg = assets.stream().map(a -> a.getType() + ": " + a.getUrl())
                .reduce((a, b) -> a + ", " + b).orElse("no assets");
        emitToolExecuted(sink, NODE_IMAGE_FETCH, assets, defaultAssetsMsg);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_IMAGE_FETCH, defaultAssetsMsg);
        return Map.of(
                "imageAssets", assets,
                "currentNode", NODE_IMAGE_FETCH,
                "statusMessage", "image assets collected");
    }

    private Map<String, Object> runPromptEnhanceNode(WorkflowGraphState state, App app, String message,
                                                     ChatHistory userHistory, Long userId,
                                                     FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> promptEnhanceNode 开始执行, appId={}, 图片资源数={}", "Workflow", userHistory.getAppId(), state.imageAssets().size());
        emitToolRequest(sink, NODE_PROMPT_ENHANCE, Map.of("assetCount", state.imageAssets().size()));
        String enhancedPrompt = enhancePrompt(app, message, state.prd(), state.imageAssets());
        log.info("[{}] <<< promptEnhanceNode 完成, appId={}, 增强提示词长度={}", "Workflow", userHistory.getAppId(), enhancedPrompt.length());
        emitToolExecuted(sink, NODE_PROMPT_ENHANCE, Map.of("promptLength", enhancedPrompt.length()), enhancedPrompt);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_PROMPT_ENHANCE, enhancedPrompt);
        return Map.of(
                "enhancedPrompt", enhancedPrompt,
                "currentNode", NODE_PROMPT_ENHANCE,
                "statusMessage", "prompt enhanced");
    }

    private Map<String, Object> runRouteNode(WorkflowGraphState state, App app, String message,
                                             ChatHistory userHistory, Long userId,
                                             FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> routeNode 开始执行, appId={}, 请求类型={}", "Workflow", app.getId(), app.getCodeGenType());
        emitToolRequest(sink, NODE_ROUTE, Map.of("requestedType", app.getCodeGenType() != null ? app.getCodeGenType() : "auto"));
        CodeGenType codeGenType = determineCodeGenType(app, message, state.prd());
        log.info("[{}] <<< routeNode 完成, appId={}, 路由决策={}", "Workflow", app.getId(), codeGenType.getValue());
        emitToolExecuted(sink, NODE_ROUTE, Map.of("route", codeGenType.getValue()), "Route: " + codeGenType.getValue());
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_ROUTE,
                "Route: " + codeGenType.getValue());
        return Map.of(
                "route", codeGenType.getValue(),
                "currentNode", NODE_ROUTE,
                "statusMessage", "route selected");
    }

    private Map<String, Object> runCodeGenNode(WorkflowGraphState state, App app, Long userId,
                                               ChatHistory userHistory,
                                               FluxSink<ServerSentEvent<String>> sink) {
        Long appId = app.getId();
        log.info("[{}] >>> codeGenNode 开始执行, appId={}, route={}, 重试次数={}", "Workflow", appId, state.route(), state.retryCount());
        emitToolRequest(sink, NODE_CODE_GEN, Map.of("route", state.route(), "retry", state.retryCount()));

        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getDeployKey());
        boolean hasExistingFiles = !workflowFileToolService.listFiles(rootDir).isEmpty();

        String generatedContent;
        if (hasExistingFiles) {
            // 后续迭代：使用工具调用模式，AI 自主读取和修改文件
            log.info("[{}] codeGenNode 检测到已有文件，进入迭代修改模式, appId={}", "Workflow", appId);
            generatedContent = runCodeGenIteration(rootDir, state.enhancedPrompt());
        } else {
            // 初次生成：保持流式生成
            boolean[] incrementalSaved = {false};
            generatedContent = generateCodeStream(appId, userId, state.enhancedPrompt(), state.routeType(), app.getDeployKey(), sink, incrementalSaved);
            // 格式化代码缩进
            generatedContent = formatCodeBlocks(generatedContent);
            // HTML 模式需要最终写入；非 HTML 模式增量保存未触发时兜底全量写入
            if (state.routeType() == CodeGenType.HTML) {
                CodePersistStrategy strategy = codePersistStrategyRegistry.getStrategy(state.routeType());
                strategy.persist(app.getDeployKey(), normalizePayload(state.routeType(), generatedContent));
            } else if (!incrementalSaved[0]) {
                log.warn("[Workflow] 增量保存未触发，执行全量写入, appId={}", appId);
                CodePersistStrategy strategy = codePersistStrategyRegistry.getStrategy(state.routeType());
                strategy.persist(app.getDeployKey(), generatedContent);
            }
        }

        log.info("[{}] <<< codeGenNode 完成, appId={}, 生成内容长度={}", "Workflow", appId, generatedContent.length());
        emitToolExecuted(sink, NODE_CODE_GEN, Map.of("length", generatedContent.length(), "retry", state.retryCount(), "persisted", true));
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_CODE_GEN, "代码已生成并保存");
        return Map.of(
                "generatedContent", generatedContent,
                "currentNode", NODE_CODE_GEN,
                "statusMessage", "code generated and persisted");
    }

    /**
     * 后续迭代：AI 通过工具调用读取现有文件并修改
     */
    private String runCodeGenIteration(Path rootDir, String enhancedPrompt) {
        WorkflowFileTools fileTools = new WorkflowFileTools(rootDir, workflowFileToolService);
        List<ToolSpecification> toolSpecs = ToolSpecifications.toolSpecificationsFrom(fileTools);

        String systemPrompt = promptLoader.load("workflow_code_iteration.txt");
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from("请根据用户需求修改项目代码。先列出文件结构，再读取需要修改的文件，然后进行修改。\n\n用户需求:\n" + enhancedPrompt));

        int maxRounds = 15;
        for (int round = 0; round < maxRounds; round++) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse chatResponse = reviewChatModel.chat(chatRequest);
            AiMessage aiMessage = chatResponse.aiMessage();
            messages.add(aiMessage);

            if (aiMessage.hasToolExecutionRequests()) {
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    log.info("[{}] codeGenIteration 执行工具: {} (round={})", "Workflow", toolRequest.name(), round);
                    String toolResult = executeToolCall(fileTools, toolRequest);
                    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
                }
            } else {
                // AI 完成修改
                break;
            }
        }

        return readMainFileContent(rootDir);
    }

    private Map<String, Object> runQualityCheckNode(WorkflowGraphState state, ChatHistory userHistory, Long userId, FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> qualityCheckNode 开始执行, 当前重试次数={}", "Workflow", state.retryCount());
        emitToolRequest(sink, NODE_QUALITY_CHECK, Map.of("retry", state.retryCount()));
        QualityCheckResult qualityCheckResult = qualityCheck(state.routeType(), state.generatedContent());
        log.info("[{}] <<< qualityCheckNode 完成, 通过={}, 原因={}", "Workflow", qualityCheckResult.pass(), qualityCheckResult.reason());
        emitToolExecuted(sink, NODE_QUALITY_CHECK, qualityCheckResult);
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_QUALITY_CHECK, qualityCheckResult.reason());
        int nextRetry = qualityCheckResult.pass() ? state.retryCount() : state.retryCount() + 1;
        return Map.of(
                "qualityPass", qualityCheckResult.pass(),
                "retryCount", nextRetry,
                "currentNode", NODE_QUALITY_CHECK,
                "statusMessage", qualityCheckResult.reason(),
                "errorMessage", qualityCheckResult.pass() ? "" : qualityCheckResult.reason());
    }

    private Map<String, Object> runCodeFixNode(WorkflowGraphState state, App app, Long userId,
                                               ChatHistory userHistory,
                                               FluxSink<ServerSentEvent<String>> sink) {
        Long appId = app.getId();
        String fixReason = state.errorMessage();
        log.info("[{}] >>> codeFixNode 开始执行, appId={}, 修复原因={}", "Workflow", appId, fixReason);
        emitToolRequest(sink, NODE_CODE_FIX, Map.of("reason", fixReason, "retry", state.retryCount()));

        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getDeployKey());
        WorkflowFileTools fileTools = new WorkflowFileTools(rootDir, workflowFileToolService);
        List<ToolSpecification> toolSpecs = ToolSpecifications.toolSpecificationsFrom(fileTools);

        // 构建 system prompt
        String systemPrompt = String.format(promptLoader.load("workflow_code_fix.txt"), fixReason);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from("请根据上述失败原因修复项目代码。先列出文件结构，再定位问题并修复。"));

        // tool-calling 循环，最多 15 轮
        int maxRounds = 15;
        String fixSummary = "";
        for (int round = 0; round < maxRounds; round++) {
            ChatRequest chatRequest = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(toolSpecs)
                    .build();
            ChatResponse chatResponse = reviewChatModel.chat(chatRequest);
            AiMessage aiMessage = chatResponse.aiMessage();
            messages.add(aiMessage);

            // 没有工具调用请求 → AI 给出了最终回复
            if (aiMessage.hasToolExecutionRequests()) {
                // 执行所有工具调用
                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    log.info("[{}] codeFixNode 执行工具: {} (round={})", "Workflow", toolRequest.name(), round);
                    String toolResult = executeToolCall(fileTools, toolRequest);
                    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
                }
            } else {
                // AI 返回纯文本，修复完成
                fixSummary = aiMessage.text();
                break;
            }
        }

        log.info("[{}] <<< codeFixNode 完成, appId={}, 修复摘要长度={}", "Workflow", appId, fixSummary.length());
        emitToolExecuted(sink, NODE_CODE_FIX, Map.of("summary", fixSummary), fixSummary);
        saveNodeMessage(appId, userId, userHistory.getId(), NODE_CODE_FIX, fixSummary);
        // 读取修复后的磁盘文件内容，供质量检查节点使用
        String updatedContent = readMainFileContent(rootDir);
        return Map.of(
                "generatedContent", updatedContent,
                "lastFixReason", "",
                "currentNode", NODE_CODE_FIX,
                "statusMessage", "code fix applied");
    }

    private String executeToolCall(WorkflowFileTools fileTools, ToolExecutionRequest request) {
        try {
            String name = request.name();
            String arguments = request.arguments();
            JsonNode argsNode = objectMapper.readTree(arguments);

            return switch (name) {
                case "listFiles" -> {
                    List<String> files = fileTools.listFiles();
                    yield String.join("\n", files);
                }
                case "readFile" -> fileTools.readFile(argsNode.path("relativePath").asText(""));
                case "writeFile" -> fileTools.writeFile(
                        argsNode.path("relativePath").asText(""),
                        argsNode.path("content").asText(""));
                case "editFile" -> fileTools.editFile(
                        argsNode.path("relativePath").asText(""),
                        argsNode.path("originalContent").asText(""),
                        argsNode.path("newContent").asText(""));
                case "deleteFile" -> fileTools.deleteFile(argsNode.path("relativePath").asText(""));
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            log.warn("Tool execution failed: {}, error: {}", request.name(), e.getMessage());
            return "Tool execution error: " + e.getMessage();
        }
    }

    private Map<String, Object> runPersistNode(WorkflowGraphState state, App app, Long userId,
                                               ChatHistory userHistory, FluxSink<ServerSentEvent<String>> sink) {
        log.info("[{}] >>> persistNode 开始执行, appId={}, deployKey={}, route={}", "Workflow", app.getId(), app.getDeployKey(), state.route());
        emitToolRequest(sink, NODE_PERSIST, Map.of("route", state.route()));
        CodePersistStrategy strategy = codePersistStrategyRegistry.getStrategy(state.routeType());
        strategy.persist(app.getDeployKey(), normalizePayload(state.routeType(), state.generatedContent()));
        log.info("[{}] <<< persistNode 完成, appId={}, 代码已持久化", "Workflow", app.getId());
        emitToolExecuted(sink, NODE_PERSIST, Map.of("deployKey", app.getDeployKey(), "route", state.route()));
        saveNodeMessage(userHistory.getAppId(), userId, userHistory.getId(), NODE_PERSIST, "artifacts persisted");
        return Map.of(
                "currentNode", NODE_PERSIST,
                "statusMessage", "artifacts persisted");
    }

    private Map<String, Object> runFailNode(WorkflowGraphState state, FluxSink<ServerSentEvent<String>> sink) {
        log.error("[{}] failNode 触发, 重试次数={}, 原因={}", "Workflow", state.retryCount(), state.errorMessage());
        emitToolExecuted(sink, NODE_FAIL, Map.of("retryCount", state.retryCount(), "reason", state.errorMessage()));
        return Map.of(
                "currentNode", NODE_FAIL,
                "statusMessage", state.errorMessageOr("quality check failed"));
    }

    private String resolveQualityEdge(WorkflowGraphState state) {
        if (state.qualityPass()) {
            log.info("[{}] 质量检查通过, 工作流完成", "Workflow");
            return "pass";
        }
        if (state.retryCount() <= workflowProperties.getMaxQualityRetry()) {
            log.warn("[{}] 质量检查未通过, 进入修复阶段 (第{}次)", "Workflow", state.retryCount());
            return "fix";
        }
        log.error("[{}] 质量检查重试次数耗尽({}), 进入失败节点", "Workflow", state.retryCount());
        return "failed";
    }

    private String resolveIntentEdge(WorkflowGraphState state) {
        String intent = state.intent();
        log.info("[{}] 意图路由: intent={}", "Workflow", intent);
        return switch (intent) {
            case "visual_edit" -> "visual_edit";
            case "chat" -> "chat";
            default -> "coding";
        };
    }

    private ReviewResult reviewPrompt(String prompt) {
        try {
            String reviewPromptText = promptLoader.load("prompt_review.txt") + prompt;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(reviewPromptText));
            JsonNode node = objectMapper.readTree(extractJson(response.aiMessage().text().trim()));
            return new ReviewResult(
                    node.path("safe").asBoolean(true),
                    node.path("reason").asText(""));
        } catch (Exception e) {
            return new ReviewResult(true, "");
        }
    }

    private String generatePrd(App app, String message) {
        try {
            String appInfo = "\n应用名称: " + (app.getAppName() == null ? "CodeFreeX App" : app.getAppName())
                    + "\n用户需求: " + message
                    + "\n代码生成类型: " + (app.getCodeGenType() == null ? "html" : app.getCodeGenType());
            String prompt = promptLoader.load("workflow_prd_gen.txt") + appInfo;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            String prd = response.aiMessage().text().trim();
            if (!prd.isBlank()) {
                return prd;
            }
        } catch (Exception e) {
            log.warn("AI PRD generation failed, fallback to template, appId={}", app.getId(), e);
        }
        return """
                # Product Brief
                - appName: %s
                - goal: %s
                - codeGenType: %s
                - priority: preview first, deploy ready
                """.formatted(
                app.getAppName() == null ? "CodeFreeX App" : app.getAppName(),
                message,
                app.getCodeGenType() == null ? "html" : app.getCodeGenType());
    }

    private String enhancePrompt(App app, String message, String prd, List<WorkflowImageAsset> assets) {
        try {
            StringBuilder context = new StringBuilder();
            context.append("\n应用名称: ").append(app.getAppName())
                    .append("\n应用描述: ").append(app.getDescription())
                    .append("\n用户需求: ").append(message)
                    .append("\nPRD:\n").append(prd)
                    .append("\n图片资源:\n");
            for (WorkflowImageAsset asset : assets) {
                context.append("- ").append(asset.getType()).append(": ")
                        .append(asset.getKeyword()).append(" -> ")
                        .append(asset.getUrl()).append('\n');
            }
            String prompt = promptLoader.load("workflow_prompt_enhance.txt") + context;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            String enhanced = response.aiMessage().text().trim();
            if (!enhanced.isBlank()) {
                return enhanced;
            }
        } catch (Exception e) {
            log.warn("AI prompt enhance failed, fallback to template", e);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("APP NAME: ").append(app.getAppName()).append('\n');
        builder.append("DESCRIPTION: ").append(app.getDescription()).append('\n');
        builder.append("USER REQUEST: ").append(message).append('\n');
        builder.append("PRD:\n").append(prd).append('\n');
        builder.append("IMAGE ASSETS:\n");
        for (WorkflowImageAsset asset : assets) {
            builder.append("- ").append(asset.getType()).append(": ")
                    .append(asset.getKeyword()).append(" -> ")
                    .append(asset.getUrl()).append('\n');
        }
        return builder.toString();
    }

    private CodeGenType determineCodeGenType(App app, String message, String prd) {
        if (app.getCodeGenType() != null && !app.getCodeGenType().isBlank()) {
            return CodeGenType.normalize(app.getCodeGenType());
        }
        try {
            StringBuilder sb = new StringBuilder(promptLoader.load("workflow_route.txt"));
            if (prd != null && !prd.isBlank()) {
                sb.append("\n\nPRD文档：\n").append(prd);
            }
            sb.append("\n\n用户原始需求：\n").append(message);
            ChatResponse response = reviewChatModel.chat(UserMessage.from(sb.toString()));
            AiMessage aiMessage = response.aiMessage();
            String responseText = aiMessage.text();
            if (responseText == null || responseText.isBlank()) {
                log.warn("AI route response text is null, aiMessage={}", aiMessage);
            } else {
                String json = extractJson(responseText.trim());
                if (json != null) {
                    JsonNode node = objectMapper.readTree(json);
                    String route = node.path("route").asText("html");
                    return CodeGenType.normalize(route);
                }
                log.warn("AI route response has no JSON, text={}", responseText);
            }
        } catch (Exception e) {
            log.warn("AI route determination failed, fallback to keyword matching", e);
        }
        String text = message.toLowerCase();
        if (text.contains("vue")) {
            return CodeGenType.VUE;
        }
        if (text.contains("multi")) {
            return CodeGenType.MULTI_FILE;
        }
        return CodeGenType.HTML;
    }

    private String generateCodeStream(Long appId, Long userId, String enhancedPrompt, CodeGenType codeGenType,
                                      String deployKey, FluxSink<ServerSentEvent<String>> sink,
                                      boolean[] incrementalSaved) {
        log.info("[{}] 开始流式代码生成, appId={}, codeGenType={}, 提示词长度={}", "Workflow", appId, codeGenType, enhancedPrompt.length());
        String systemPrompt = promptLoader.load(resolvePromptTemplate(codeGenType));
        ChatMemory chatMemory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        chatMemory.add(UserMessage.from(enhancedPrompt));

        StreamingChatModel streamingModel = applicationContext.getBean(StreamingChatModel.class);
        List<ChatMessage> messages = new ArrayList<>(chatMemory.messages());
        StringBuilder responseBuilder = new StringBuilder();
        RuntimeException[] errorHolder = new RuntimeException[1];
        CountDownLatch latch = new CountDownLatch(1);

        // 流式增量保存：追踪已完成的文件块偏移量
        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        if (codeGenType != CodeGenType.HTML) {
            try {
                Files.createDirectories(rootDir);
            } catch (Exception e) {
                log.warn("[Workflow] 创建输出目录失败: {}", e.getMessage());
            }
        }
        int[] savedOffset = {0};

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
                emitAiResponse(sink, partialResponse);

                // 非 HTML 模式：检测已闭合的文件块，立即写入磁盘
                if (codeGenType != CodeGenType.HTML) {
                    savedOffset[0] = saveCompletedFileBlocks(responseBuilder.toString(), savedOffset[0], rootDir, incrementalSaved);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorHolder[0] = new RuntimeException(error);
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.MINUTES)) {
                log.error("[{}] 流式代码生成超时(5分钟), appId={}", "Workflow", appId);
                throw new RuntimeException("AI stream timeout after 5 minutes");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[{}] 流式代码生成被中断, appId={}", "Workflow", appId);
            throw new RuntimeException("AI stream interrupted", e);
        }
        if (errorHolder[0] != null) {
            log.error("[{}] 流式代码生成出错, appId={}, error={}", "Workflow", appId, errorHolder[0].getMessage());
            throw errorHolder[0];
        }
        log.info("[{}] 流式代码生成完成, appId={}, 响应长度={}", "Workflow", appId, responseBuilder.length());
        return responseBuilder.toString();
    }

    /**
     * 从 searchFrom 位置开始，检测已闭合的文件块并写入磁盘。
     * 返回新的搜索起始位置（最后一个匹配的结束位置），下次从这里继续搜索。
     */
    private static final Pattern FILE_BLOCK_PATTERN =
            Pattern.compile("```file:([^\\n\\r]+)\\R([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private int saveCompletedFileBlocks(String content, int searchFrom, Path rootDir, boolean[] incrementalSaved) {
        try {
            Matcher matcher = FILE_BLOCK_PATTERN.matcher(content);
            matcher.region(searchFrom, content.length());
            int lastMatchEnd = searchFrom;
            while (matcher.find()) {
                String fileName = matcher.group(1).trim();
                String fileContent = matcher.group(2).strip();
                workflowFileToolService.writeFile(rootDir, fileName, fileContent);
                log.info("[Workflow] 流式增量保存文件: {}", fileName);
                lastMatchEnd = matcher.end();
                incrementalSaved[0] = true;
            }
            return lastMatchEnd;
        } catch (Exception e) {
            log.warn("[Workflow] 流式增量保存失败: {}", e.getMessage());
            return searchFrom;
        }
    }

    private QualityCheckResult qualityCheck(CodeGenType codeGenType, String generatedContent) {
        if (generatedContent == null || generatedContent.isBlank()) {
            return new QualityCheckResult(false, "AI returned empty content");
        }
        if (generatedContent.contains("QUALITY_FAIL")) {
            return new QualityCheckResult(false, "Triggered quality fail marker");
        }
        if (codeGenType == CodeGenType.HTML && extractHtml(generatedContent).isBlank()) {
            return new QualityCheckResult(false, "HTML block missing");
        }
        if (codeGenType != CodeGenType.HTML && FileBundleParser.parse(generatedContent).isEmpty()) {
            return new QualityCheckResult(false, "File blocks missing");
        }
        return new QualityCheckResult(true, "ok");
    }

    private String normalizePayload(CodeGenType codeGenType, String generatedContent) {
        if (codeGenType != CodeGenType.HTML) {
            return generatedContent;
        }
        String html = extractHtml(generatedContent);
        return html.isBlank() ? generatedContent : html;
    }

    private String extractHtml(String generatedContent) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(generatedContent);
        if (matcher.find()) {
            return matcher.group(1).strip();
        }
        return generatedContent.contains("<html") || generatedContent.contains("<div")
                ? generatedContent.strip()
                : "";
    }

    private String resolvePromptTemplate(CodeGenType codeGenType) {
        return switch (codeGenType) {
            case HTML -> "workflow_html.txt";
            case MULTI_FILE -> "workflow_multi_file.txt";
            case VUE -> "workflow_vue.txt";
        };
    }

    private void persistFailure(Long appId, Long userId, ChatHistory userHistory, String errorMessage) {
        try {
            chatHistoryService.saveAiMessage(appId, userId, errorMessage, userHistory.getId());
        } catch (Exception ignored) {
            log.warn("Persist workflow failure message failed, appId={}", appId);
        }
    }

    private void saveNodeMessage(Long appId, Long userId, Long parentId, String node, String content) {
        try {
            // chatDirectNode: 纯文本对话回复，不加 STATUS 标记
            if (NODE_CHAT_DIRECT.equals(node)) {
                chatHistoryService.saveAiMessage(appId, userId, content, parentId);
            } else {
                String statusJson = buildStatusMarker(node, content);
                String wrapped = "<!--STATUS:" + statusJson + "-->\n" + content;
                chatHistoryService.saveAiMessage(appId, userId, wrapped, parentId);
            }
        } catch (Exception e) {
            log.warn("Save node message failed, node={}", node, e);
        }
    }

    private String buildStatusMarker(String node, String content) {
        try {
            Map<String, Object> status = new LinkedHashMap<>();
            switch (node) {
                case NODE_PROMPT_REVIEW -> {
                    status.put("icon", "🛡️");
                    boolean passed = content.toLowerCase().contains("passed");
                    status.put("label", "检查内容安全性");
                    status.put("detail", passed ? "通过" : "未通过");
                    status.put("status", passed ? "done" : "error");
                }
                case NODE_INTENT_CLASSIFY -> {
                    status.put("icon", "🔍");
                    String detail;
                    if (content.contains("可视化编辑")) {
                        detail = "可视化编辑";
                    } else if (content.contains("编码")) {
                        detail = "开发任务";
                    } else {
                        detail = "普通对话";
                    }
                    status.put("label", "分析用户意图");
                    status.put("detail", detail);
                    status.put("status", "done");
                }
                case NODE_CHAT_DIRECT -> {
                    status.put("icon", "💬");
                    status.put("label", "对话回复完成");
                    status.put("status", "done");
                }
                case NODE_VISUAL_EDIT -> {
                    status.put("icon", "🎨");
                    status.put("label", "执行修改");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_EDIT_COMPLETE -> {
                    status.put("icon", "✅");
                    status.put("label", "修改完成");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_PRD_GEN -> {
                    status.put("icon", "📋");
                    status.put("label", "生成需求文档");
                    status.put("detail", "PRD.md");
                    status.put("downloadAction", "prd");
                    status.put("status", "done");
                }
                case NODE_IMAGE_PLAN -> {
                    status.put("icon", "🖼️");
                    status.put("label", "规划图片资源");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_IMAGE_FETCH -> {
                    status.put("icon", "🖼️");
                    status.put("label", "获取图片素材");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_PROMPT_ENHANCE -> {
                    status.put("icon", "✨");
                    status.put("label", "优化提示词");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_ROUTE -> {
                    status.put("icon", "🎯");
                    status.put("label", "确定生成方案");
                    status.put("detail", content.replace("Route: ", ""));
                    status.put("status", "done");
                }
                case NODE_CODE_GEN -> {
                    status.put("icon", "💻");
                    status.put("label", "编写代码");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                case NODE_QUALITY_CHECK -> {
                    status.put("icon", "🔍");
                    status.put("label", "代码质量检查");
                    boolean passed = content.toLowerCase().contains("ok") || content.toLowerCase().contains("pass");
                    status.put("detail", passed ? "通过" : "未通过");
                    status.put("status", passed ? "done" : "warning");
                }
                case NODE_CODE_FIX -> {
                    status.put("icon", "🔧");
                    status.put("label", "修复代码");
                    status.put("detail", "修复完成");
                    status.put("status", "done");
                }
                case NODE_PERSIST -> {
                    status.put("icon", "✅");
                    status.put("label", "生成完成");
                    status.put("detail", "已完成");
                    status.put("status", "done");
                }
                default -> {
                    status.put("icon", "⚙️");
                    status.put("label", "处理完成");
                    status.put("status", "done");
                }
            }
            return objectMapper.writeValueAsString(status);
        } catch (Exception e) {
            return "{\"icon\":\"\\u2699\\uFE0F\",\"label\":\"处理完成\",\"status\":\"done\"}";
        }
    }

    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return null;
    }

    private void emitToolRequest(FluxSink<ServerSentEvent<String>> sink, String node, Object data) {
        emitEvent(sink, "tool_request", Map.of("node", node, "data", data));
        sleepBriefly();
    }

    private void emitToolExecuted(FluxSink<ServerSentEvent<String>> sink, String node, Object data) {
        emitToolExecuted(sink, node, data, null);
    }

    private void emitToolExecuted(FluxSink<ServerSentEvent<String>> sink, String node, Object data, String displayMessage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("node", node);
        m.put("data", data);
        if (displayMessage != null && !displayMessage.isBlank()) {
            m.put("message", displayMessage);
        }
        emitEvent(sink, "tool_executed", m);
        sleepBriefly();
    }

    private void emitAiResponse(FluxSink<ServerSentEvent<String>> sink, String content) {
        emitEvent(sink, "ai_response", content);
    }

    private void emitError(FluxSink<ServerSentEvent<String>> sink, String message) {
        emitEvent(sink, "error", message);
    }

    private void emitDone(FluxSink<ServerSentEvent<String>> sink) {
        emitEvent(sink, "done", "");
    }

    private void emitEvent(FluxSink<ServerSentEvent<String>> sink, String type, Object data) {
        if (sink.isCancelled()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(new WorkflowEvent(type, data));
            sink.next(ServerSentEvent.<String>builder().data(payload).build());
        } catch (Exception e) {
            log.warn("[Workflow] SSE 发送失败: {}", e.getMessage());
        }
    }

    private void sleepBriefly() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateStatus(Long appId, String status, String currentNode, String route,
                              Integer retryCount, String message) {
        WorkflowStatusResponse response = statusMap.computeIfAbsent(appId, id -> new WorkflowStatusResponse());
        response.setAppId(appId);
        response.setStatus(status);
        response.setCurrentNode(currentNode);
        response.setRoute(route);
        response.setRetryCount(retryCount);
        response.setMessage(message);
        response.setUpdateTime(LocalDateTime.now());
    }

    private App checkAppAndOwner(Long appId, Long userId) {
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "App not found");
        }
        if (userId == null || !userId.equals(app.getUserId())) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "No permission for this app");
        }
        return app;
    }

    private record ReviewResult(boolean safe, String reason) {
    }

    private record QualityCheckResult(boolean pass, String reason) {
    }

    static class WorkflowGraphState extends AgentState {

        WorkflowGraphState(Map<String, Object> initData) {
            super(initData);
        }

        boolean blocked() {
            return value("blocked", false);
        }

        boolean reviewSafe() {
            return value("reviewSafe", true);
        }

        String route() {
            return value("route", "html");
        }

        CodeGenType routeType() {
            return CodeGenType.normalize(route());
        }

        String prd() {
            return value("prd", "");
        }

        @SuppressWarnings("unchecked")
        List<String> imagePlan() {
            return value("imagePlan", List.of());
        }

        @SuppressWarnings("unchecked")
        List<WorkflowImageAsset> imageAssets() {
            Object raw = data().get("imageAssets");
            if (raw == null) {
                return List.of();
            }
            if (raw instanceof List<?> list) {
                if (list.isEmpty()) {
                    return List.of();
                }
                // 防止 devtools RestartClassLoader 导致的 ClassCastException，
                // 通过 ObjectMapper 做安全的跨 ClassLoader 转换
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String json = mapper.writeValueAsString(list);
                    return mapper.readValue(json,
                            mapper.getTypeFactory().constructCollectionType(List.class, WorkflowImageAsset.class));
                } catch (Exception e) {
                    return List.of();
                }
            }
            return List.of();
        }

        String enhancedPrompt() {
            return value("enhancedPrompt", "");
        }

        String generatedContent() {
            return value("generatedContent", "");
        }

        boolean qualityPass() {
            return value("qualityPass", false);
        }

        int retryCount() {
            return value("retryCount", 0);
        }

        String currentNode() {
            return value("currentNode", "none");
        }

        String statusMessage() {
            return value("statusMessage", "");
        }

        String errorMessage() {
            return value("errorMessage", "");
        }

        String intent() {
            return value("intent", "coding");
        }

        boolean isCodingTask() {
            return "coding".equals(intent());
        }

        boolean isVisualEdit() {
            return "visual_edit".equals(intent());
        }

        String chatResponse() {
            return value("chatResponse", "");
        }

        String lastFixReason() {
            return value("lastFixReason", "");
        }

        String errorMessageOr(String fallback) {
            String message = errorMessage();
            return message == null || message.isBlank() ? fallback : message;
        }
    }

    // ============ 代码格式化 ============

    /**
     * 检测代码是否已有缩进（任一非空行以空格或 tab 开头）
     */
    private boolean hasIndentation(String code) {
        for (String line : code.split("\n")) {
            if (!line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对 markdown 中的代码块进行格式化。
     * 只处理 html 代码块；若代码块内已有缩进则跳过。
     */
    private String formatCodeBlocks(String markdown) {
        if (markdown == null || !markdown.contains("```")) return markdown;

        Matcher matcher = HTML_CODE_PATTERN.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String code = matcher.group(1);
            String formatted = hasIndentation(code) ? code : reindentHtml(code);
            matcher.appendReplacement(sb, Matcher.quoteReplacement("```html\n" + formatted + "\n```"));
        }
        if (!found) return markdown;
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 对无缩进的 HTML 代码重新缩进，按标签嵌套层级添加缩进。
     * <style> / <script> 内部内容原样保留，仅做一级缩进。
     */
    private String reindentHtml(String code) {
        StringBuilder sb = new StringBuilder();
        int level = 0;
        boolean inStyle = false;
        boolean inScript = false;

        for (String rawLine : code.split("\n")) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            // ---- <style> / <script> 块结束 ----
            if (inStyle && trimmed.startsWith("</style")) {
                inStyle = false;
                level = Math.max(0, level - 1);
                sb.append("  ".repeat(level)).append(trimmed).append("\n");
                continue;
            }
            if (inScript && trimmed.startsWith("</script")) {
                inScript = false;
                level = Math.max(0, level - 1);
                sb.append("  ".repeat(level)).append(trimmed).append("\n");
                continue;
            }

            // ---- <style> / <script> 块内部，保持原样，仅缩进一级 ----
            if (inStyle || inScript) {
                sb.append("  ".repeat(level + 1)).append(trimmed).append("\n");
                continue;
            }

            // ---- 独立的关闭标签：先缩进再输出 ----
            boolean isStandaloneClose = trimmed.startsWith("</")
                    && !trimmed.substring(2).contains("<")
                    && trimmed.endsWith(">");
            if (isStandaloneClose) {
                level = Math.max(0, level - 1);
                sb.append("  ".repeat(level)).append(trimmed).append("\n");
                continue;
            }

            // ---- 其他行：先输出再判断是否升级层级 ----
            sb.append("  ".repeat(level)).append(trimmed).append("\n");

            if (trimmed.startsWith("<") && !trimmed.startsWith("<!") && !trimmed.startsWith("</")) {
                Matcher tm = TAG_NAME_PATTERN.matcher(trimmed);
                if (tm.find()) {
                    String tag = tm.group(1).toLowerCase();
                    boolean selfClose = trimmed.endsWith("/>");
                    boolean voidTag = VOID_TAGS.contains(tag);
                    boolean sameLineClose = !selfClose && !voidTag && trimmed.contains("</");
                    if (!selfClose && !voidTag && !sameLineClose) {
                        level++;
                        if ("style".equals(tag)) inStyle = true;
                        if ("script".equals(tag)) inScript = true;
                    }
                }
            }
        }
        return sb.toString().stripTrailing();
    }
}
