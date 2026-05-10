package cn.wanyj.codefreex.service.impl;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.config.AiConfig;
import cn.wanyj.codefreex.config.AppRuntimeConfig;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.AppMapper;
import cn.wanyj.codefreex.model.dto.request.VisualEditRequest;
import cn.wanyj.codefreex.model.dto.response.WorkflowEvent;
import cn.wanyj.codefreex.model.dto.response.WorkflowImageAsset;
import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.model.entity.ChatHistory;
import cn.wanyj.codefreex.model.enums.AppStatus;
import cn.wanyj.codefreex.model.enums.CodeGenType;
import cn.wanyj.codefreex.service.AiWorkflowService;
import cn.wanyj.codefreex.service.AppService;
import cn.wanyj.codefreex.service.ChatHistoryService;
import cn.wanyj.codefreex.service.ChatMemoryService;
import cn.wanyj.codefreex.service.CodePersistStrategyRegistry;
import cn.wanyj.codefreex.service.WorkflowFileToolService;
import cn.wanyj.codefreex.service.WorkflowImageService;
import cn.wanyj.codefreex.service.strategy.CodePersistStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.springframework.context.ApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P7 workflow service implementation
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

        return Flux.create(sink -> {
            ChatHistory userHistory = null;
            try {
                appService.updateAppStatus(appId, AppStatus.GENERATING.getValue());
                userHistory = chatHistoryService.saveUserMessage(appId, userId, message);
                updateStatus(appId, "running", NODE_PROMPT_GUARD, app.getCodeGenType(), 0, "workflow started");

                CompiledGraph<WorkflowGraphState> graph = buildWorkflowGraph(appId, userId, app, message, sink);
                graph.setMaxIterations(16);

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
                    persistFailure(appId, userId, userHistory, errorMessage);
                    appService.updateAppStatus(appId, AppStatus.DRAFT.getValue());
                    updateStatus(appId, "blocked", finalState.currentNode(), finalState.route(),
                            finalState.retryCount(), errorMessage);
                    emitError(sink, errorMessage);
                    emitDone(sink);
                    sink.complete();
                    return;
                }

                if (!finalState.qualityPass()) {
                    throw new RuntimeException(finalState.errorMessageOr("Quality check failed"));
                }

                String generatedContent = finalState.generatedContent();
                chatHistoryService.saveAiMessage(appId, userId, generatedContent, userHistory.getId());
                ChatMemory chatMemory = chatMemoryService.getChatMemory(
                        appId, userId, promptLoader.load(resolvePromptTemplate(finalState.routeType())));
                chatMemory.add(AiMessage.from(generatedContent));

                appService.updateAppStatus(appId, AppStatus.GENERATED.getValue());
                updateStatus(appId, "completed", NODE_PERSIST, finalState.route(),
                        finalState.retryCount(), "workflow completed");
                emitDone(sink);
                sink.complete();
            } catch (Exception e) {
                log.error("P7 workflow failed, appId={}", appId, e);
                appService.updateAppStatus(appId, AppStatus.DRAFT.getValue());
                updateStatus(appId, "failed", "error", app.getCodeGenType(), 0, e.getMessage());
                if (userHistory != null) {
                    persistFailure(appId, userId, userHistory, "Workflow failed: " + e.getMessage());
                }
                emitError(sink, e.getMessage());
                emitDone(sink);
                sink.complete();
            }
        });
    }

    @Override
    public Flux<ServerSentEvent<String>> visualEdit(VisualEditRequest request) {
        Long userId = UserContext.getLoginUserId();
        App app = checkAppAndOwner(request.getAppId(), userId);
        return Flux.create(sink -> {
            try {
                Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, app.getDeployKey());
                String targetFile = request.getTargetFile() == null || request.getTargetFile().isBlank()
                        ? "index.html" : request.getTargetFile();
                String originalCode = workflowFileToolService.readFile(rootDir, targetFile);

                emitToolRequest(sink, "visualEdit", Map.of(
                        "selector", request.getSelector(),
                        "targetFile", targetFile));
                String editedCode = callVisualEditModel(originalCode, request);
                workflowFileToolService.writeFile(rootDir, targetFile, editedCode);
                emitAiResponse(sink, editedCode);
                emitToolExecuted(sink, "visualEdit", Map.of("updated", true, "targetFile", targetFile));
                emitDone(sink);
                sink.complete();
            } catch (Exception e) {
                emitError(sink, e.getMessage());
                emitDone(sink);
                sink.complete();
            }
        });
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
                                                                 FluxSink<ServerSentEvent<String>> sink) throws Exception {
        StateGraph<WorkflowGraphState> graph = new StateGraph<>(WORKFLOW_SCHEMA, WorkflowGraphState::new);

        graph.addNode(NODE_PROMPT_GUARD, state -> CompletableFuture.completedFuture(runPromptGuardNode(state, message, sink)));
        graph.addNode(NODE_PROMPT_REVIEW, state -> CompletableFuture.completedFuture(runPromptReviewNode(state, message, sink)));
        graph.addNode(NODE_PRD_GEN, state -> CompletableFuture.completedFuture(runPrdGenNode(state, app, message, sink)));
        graph.addNode(NODE_IMAGE_PLAN, state -> CompletableFuture.completedFuture(runImagePlanNode(state, sink)));
        graph.addNode(NODE_IMAGE_FETCH, state -> CompletableFuture.completedFuture(runImageFetchNode(state, message, sink)));
        graph.addNode(NODE_PROMPT_ENHANCE, state -> CompletableFuture.completedFuture(runPromptEnhanceNode(state, app, message, sink)));
        graph.addNode(NODE_ROUTE, state -> CompletableFuture.completedFuture(runRouteNode(state, app, message, sink)));
        graph.addNode(NODE_CODE_GEN, state -> CompletableFuture.completedFuture(runCodeGenNode(state, appId, userId, sink)));
        graph.addNode(NODE_QUALITY_CHECK, state -> CompletableFuture.completedFuture(runQualityCheckNode(state, sink)));
        graph.addNode(NODE_PERSIST, state -> CompletableFuture.completedFuture(runPersistNode(state, app, sink)));
        graph.addNode(NODE_FAIL, state -> CompletableFuture.completedFuture(runFailNode(state, sink)));

        graph.addEdge(StateGraph.START, NODE_PROMPT_GUARD);
        graph.addConditionalEdges(NODE_PROMPT_GUARD,
                state -> CompletableFuture.completedFuture(state.blocked() ? "blocked" : "passed"),
                Map.of("blocked", StateGraph.END, "passed", NODE_PROMPT_REVIEW));
        graph.addConditionalEdges(NODE_PROMPT_REVIEW,
                state -> CompletableFuture.completedFuture(state.reviewSafe() ? "passed" : "rejected"),
                Map.of("passed", NODE_PRD_GEN, "rejected", StateGraph.END));
        graph.addEdge(NODE_PRD_GEN, NODE_IMAGE_PLAN);
        graph.addEdge(NODE_IMAGE_PLAN, NODE_IMAGE_FETCH);
        graph.addEdge(NODE_IMAGE_FETCH, NODE_PROMPT_ENHANCE);
        graph.addEdge(NODE_PROMPT_ENHANCE, NODE_ROUTE);
        graph.addEdge(NODE_ROUTE, NODE_CODE_GEN);
        graph.addEdge(NODE_CODE_GEN, NODE_QUALITY_CHECK);
        graph.addConditionalEdges(NODE_QUALITY_CHECK,
                state -> CompletableFuture.completedFuture(resolveQualityEdge(state)),
                Map.of("retry", NODE_CODE_GEN, "persist", NODE_PERSIST, "failed", NODE_FAIL));
        graph.addEdge(NODE_PERSIST, StateGraph.END);
        graph.addEdge(NODE_FAIL, StateGraph.END);

        return graph.compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());
    }

    private Map<String, Object> runPromptGuardNode(WorkflowGraphState state, String message,
                                                   FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_PROMPT_GUARD, Map.of("message", message));
        String lower = message == null ? "" : message.toLowerCase();
        for (String keyword : BLOCKED_KEYWORDS) {
            if (lower.contains(keyword)) {
                emitToolExecuted(sink, NODE_PROMPT_GUARD, Map.of("result", "blocked", "keyword", keyword));
                return Map.of(
                        "blocked", true,
                        "currentNode", NODE_PROMPT_GUARD,
                        "statusMessage", "prompt blocked",
                        "errorMessage", "High-risk prompt blocked");
            }
        }
        emitToolExecuted(sink, NODE_PROMPT_GUARD, Map.of("result", "pass"));
        return Map.of(
                "blocked", false,
                "currentNode", NODE_PROMPT_GUARD,
                "statusMessage", "prompt guard passed");
    }

    private Map<String, Object> runPromptReviewNode(WorkflowGraphState state, String message,
                                                    FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_PROMPT_REVIEW, Map.of("message", message));
        ReviewResult reviewResult = reviewPrompt(message);
        emitToolExecuted(sink, NODE_PROMPT_REVIEW, reviewResult);
        return Map.of(
                "reviewSafe", reviewResult.safe(),
                "route", reviewResult.routeSuggestion(),
                "currentNode", NODE_PROMPT_REVIEW,
                "statusMessage", reviewResult.safe() ? "prompt review passed" : "prompt review rejected",
                "errorMessage", reviewResult.safe() ? "" : "Prompt review rejected: " + reviewResult.reason());
    }

    private Map<String, Object> runPrdGenNode(WorkflowGraphState state, App app, String message,
                                              FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_PRD_GEN, Map.of("appId", app.getId()));
        String prd = generatePrd(app, message);
        emitToolExecuted(sink, NODE_PRD_GEN, Map.of("prd", prd));
        return Map.of(
                "prd", prd,
                "currentNode", NODE_PRD_GEN,
                "statusMessage", "prd generated");
    }

    private Map<String, Object> runImagePlanNode(WorkflowGraphState state, FluxSink<ServerSentEvent<String>> sink) {
        List<String> imagePlan = List.of("content", "illustration", "diagram", "logo");
        emitToolRequest(sink, NODE_IMAGE_PLAN, Map.of("plan", imagePlan));
        emitToolExecuted(sink, NODE_IMAGE_PLAN, imagePlan);
        return Map.of(
                "imagePlan", imagePlan,
                "currentNode", NODE_IMAGE_PLAN,
                "statusMessage", "image plan ready");
    }

    private Map<String, Object> runImageFetchNode(WorkflowGraphState state, String message,
                                                  FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_IMAGE_FETCH, state.imagePlan());
        List<WorkflowImageAsset> assets = workflowImageService.collectAssets(message);
        emitToolExecuted(sink, NODE_IMAGE_FETCH, assets);
        return Map.of(
                "imageAssets", assets,
                "currentNode", NODE_IMAGE_FETCH,
                "statusMessage", "image assets collected");
    }

    private Map<String, Object> runPromptEnhanceNode(WorkflowGraphState state, App app, String message,
                                                     FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_PROMPT_ENHANCE, Map.of("assetCount", state.imageAssets().size()));
        String enhancedPrompt = enhancePrompt(app, message, state.prd(), state.imageAssets());
        emitToolExecuted(sink, NODE_PROMPT_ENHANCE, Map.of("promptLength", enhancedPrompt.length()));
        return Map.of(
                "enhancedPrompt", enhancedPrompt,
                "currentNode", NODE_PROMPT_ENHANCE,
                "statusMessage", "prompt enhanced");
    }

    private Map<String, Object> runRouteNode(WorkflowGraphState state, App app, String message,
                                             FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_ROUTE, Map.of("requestedType", app.getCodeGenType()));
        CodeGenType codeGenType = determineCodeGenType(app, state.route(), message);
        emitToolExecuted(sink, NODE_ROUTE, Map.of("route", codeGenType.getValue()));
        return Map.of(
                "route", codeGenType.getValue(),
                "currentNode", NODE_ROUTE,
                "statusMessage", "route selected");
    }

    private Map<String, Object> runCodeGenNode(WorkflowGraphState state, Long appId, Long userId,
                                               FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_CODE_GEN, Map.of("route", state.route(), "retry", state.retryCount()));
        String generatedContent = generateCodeStream(appId, userId, state.enhancedPrompt(), state.routeType(), sink);
        emitToolExecuted(sink, NODE_CODE_GEN, Map.of("length", generatedContent.length(), "retry", state.retryCount()));
        return Map.of(
                "generatedContent", generatedContent,
                "currentNode", NODE_CODE_GEN,
                "statusMessage", "code generated");
    }

    private Map<String, Object> runQualityCheckNode(WorkflowGraphState state, FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_QUALITY_CHECK, Map.of("retry", state.retryCount()));
        QualityCheckResult qualityCheckResult = qualityCheck(state.routeType(), state.generatedContent());
        emitToolExecuted(sink, NODE_QUALITY_CHECK, qualityCheckResult);
        int nextRetry = qualityCheckResult.pass() ? state.retryCount() : state.retryCount() + 1;
        return Map.of(
                "qualityPass", qualityCheckResult.pass(),
                "retryCount", nextRetry,
                "currentNode", NODE_QUALITY_CHECK,
                "statusMessage", qualityCheckResult.reason(),
                "errorMessage", qualityCheckResult.pass() ? "" : qualityCheckResult.reason());
    }

    private Map<String, Object> runPersistNode(WorkflowGraphState state, App app, FluxSink<ServerSentEvent<String>> sink) {
        emitToolRequest(sink, NODE_PERSIST, Map.of("route", state.route()));
        CodePersistStrategy strategy = codePersistStrategyRegistry.getStrategy(state.routeType());
        strategy.persist(app.getDeployKey(), normalizePayload(state.routeType(), state.generatedContent()));
        emitToolExecuted(sink, NODE_PERSIST, Map.of("deployKey", app.getDeployKey(), "route", state.route()));
        return Map.of(
                "currentNode", NODE_PERSIST,
                "statusMessage", "artifacts persisted");
    }

    private Map<String, Object> runFailNode(WorkflowGraphState state, FluxSink<ServerSentEvent<String>> sink) {
        emitToolExecuted(sink, NODE_FAIL, Map.of("retryCount", state.retryCount(), "reason", state.errorMessage()));
        return Map.of(
                "currentNode", NODE_FAIL,
                "statusMessage", state.errorMessageOr("quality check failed"));
    }

    private String resolveQualityEdge(WorkflowGraphState state) {
        if (state.qualityPass()) {
            return "persist";
        }
        if (state.retryCount() <= workflowProperties.getMaxQualityRetry()) {
            return "retry";
        }
        return "failed";
    }

    private ReviewResult reviewPrompt(String prompt) {
        try {
            String reviewPromptText = promptLoader.load("prompt_review.txt") + prompt;
            ChatResponse response = reviewChatModel.chat(UserMessage.from(reviewPromptText));
            JsonNode node = objectMapper.readTree(extractJson(response.aiMessage().text().trim()));
            return new ReviewResult(
                    node.path("safe").asBoolean(true),
                    node.path("route").asText("html"),
                    node.path("reason").asText(""));
        } catch (Exception e) {
            return new ReviewResult(true, "html", "");
        }
    }

    private String generatePrd(App app, String message) {
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

    private CodeGenType determineCodeGenType(App app, String reviewRouteSuggestion, String message) {
        if (app.getCodeGenType() != null && !app.getCodeGenType().isBlank()) {
            return CodeGenType.normalize(app.getCodeGenType());
        }
        String text = (message + " " + reviewRouteSuggestion).toLowerCase();
        if (text.contains("vue")) {
            return CodeGenType.VUE;
        }
        if (text.contains("multi")) {
            return CodeGenType.MULTI_FILE;
        }
        return CodeGenType.HTML;
    }

    private String generateCodeStream(Long appId, Long userId, String enhancedPrompt, CodeGenType codeGenType,
                                      FluxSink<ServerSentEvent<String>> sink) {
        String systemPrompt = promptLoader.load(resolvePromptTemplate(codeGenType));
        ChatMemory chatMemory = chatMemoryService.getChatMemory(appId, userId, systemPrompt);
        chatMemory.add(UserMessage.from(enhancedPrompt));

        StreamingChatModel streamingModel = applicationContext.getBean(StreamingChatModel.class);
        List<ChatMessage> messages = new ArrayList<>(chatMemory.messages());
        StringBuilder responseBuilder = new StringBuilder();
        RuntimeException[] errorHolder = new RuntimeException[1];

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                responseBuilder.append(partialResponse);
                emitAiResponse(sink, partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
            }

            @Override
            public void onError(Throwable error) {
                errorHolder[0] = new RuntimeException(error);
            }
        });
        if (errorHolder[0] != null) {
            throw errorHolder[0];
        }
        return responseBuilder.toString();
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

    private String callVisualEditModel(String originalCode, VisualEditRequest request) {
        String prompt = promptLoader.load("workflow_visual_edit.txt")
                + "\nTARGET SELECTOR: " + request.getSelector()
                + "\nORIGINAL ELEMENT:\n" + request.getSelectedHtml()
                + "\nINSTRUCTION:\n" + request.getInstruction()
                + "\nFULL CODE:\n" + originalCode;
        try {
            ChatResponse response = reviewChatModel.chat(UserMessage.from(prompt));
            String result = response.aiMessage().text().trim();
            if (!result.isBlank()) {
                return result;
            }
        } catch (Exception e) {
            log.warn("Visual edit model call failed, appId={}", request.getAppId(), e);
        }
        return originalCode.replace(
                request.getSelectedHtml(),
                request.getSelectedHtml() + "\n<!-- " + request.getInstruction() + " -->");
    }

    private void persistFailure(Long appId, Long userId, ChatHistory userHistory, String errorMessage) {
        try {
            chatHistoryService.saveAiMessage(appId, userId, errorMessage, userHistory.getId());
        } catch (Exception ignored) {
            log.warn("Persist workflow failure message failed, appId={}", appId);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private void emitToolRequest(FluxSink<ServerSentEvent<String>> sink, String node, Object data) {
        emitEvent(sink, "tool_request", Map.of("node", node, "data", data));
    }

    private void emitToolExecuted(FluxSink<ServerSentEvent<String>> sink, String node, Object data) {
        emitEvent(sink, "tool_executed", Map.of("node", node, "data", data));
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
        try {
            String payload = objectMapper.writeValueAsString(new WorkflowEvent(type, data));
            sink.next(ServerSentEvent.<String>builder().data(payload).build());
        } catch (Exception e) {
            throw new RuntimeException("Serialize SSE event failed", e);
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

    private record ReviewResult(boolean safe, String routeSuggestion, String reason) {
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
            return value("imageAssets", List.of());
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

        String errorMessageOr(String fallback) {
            String message = errorMessage();
            return message == null || message.isBlank() ? fallback : message;
        }
    }
}
