package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.mapper.ChatHistoryMapper;
import cn.wanyj.codefreex.model.dto.request.WorkflowGenerateRequest;
import cn.wanyj.codefreex.model.dto.response.WorkflowStatusResponse;
import cn.wanyj.codefreex.model.entity.UserInfo;
import cn.wanyj.codefreex.model.enums.CreditSourceType;
import cn.wanyj.codefreex.model.enums.CreditTransactionType;
import cn.wanyj.codefreex.service.AiWorkflowService;
import cn.wanyj.codefreex.service.CreditTransactionService;
import cn.wanyj.codefreex.service.UserInfoService;
import com.mybatisflex.core.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static cn.wanyj.codefreex.model.entity.table.ChatHistoryTableDef.CHAT_HISTORY;

/**
 * 工作流
 *
 * @author BanXia
 */
@Slf4j
@Tag(name = "AI 工作流")
@RestController
@RequestMapping("/ai/workflow")
@RequiredArgsConstructor
public class AiWorkflowController {

    private final AiWorkflowService aiWorkflowService;
    private final UserInfoService userInfoService;
    private final CreditTransactionService creditTransactionService;
    private final ChatHistoryMapper chatHistoryMapper;

    @Operation(summary = "AI对话工作流")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public SseEmitter generate(@Valid @RequestBody WorkflowGenerateRequest request) {
        // === 码点预扣减（仅后续对话扣减，首次生成已在创建应用时扣减） ===
        Long userId = UserContext.getLoginUserId();
        if (userId == null) {
            throw new BusinessException(ResponseCode.NOT_LOGIN_ERROR, "未登录");
        }

        // 判断是否首次生成（该应用是否有对话历史）
        long historyCount = chatHistoryMapper.selectCountByQuery(
                QueryWrapper.create().where(CHAT_HISTORY.APP_ID.eq(request.getAppId()))
        );
        boolean isFirstGenerate = historyCount == 0;

        if (!isFirstGenerate) {
            // 非首次：扣减对话轮次码点
            int cost = AppConstant.CHAT_ROUND_COST;
            UserInfo userInfo = userInfoService.getUserInfo(userId);
            if (userInfo == null || userInfo.getRemainingCredits() < cost) {
                throw new BusinessException(ResponseCode.OPERATION_ERROR,
                        "码点不足，需要 " + cost + " 码点，请先兑换码点");
            }
            userInfoService.deductCredits(userId, cost);

            UserInfo updatedUserInfo = userInfoService.getUserInfo(userId);
            creditTransactionService.recordTransaction(
                    userId,
                    CreditTransactionType.CONSUME,
                    -cost,
                    updatedUserInfo.getRemainingCredits(),
                    CreditSourceType.AI_CHAT,
                    request.getAppId(),
                    "对话轮次",
                    null
            );
        }

        // === 开始工作流 ===
        SseEmitter emitter = new SseEmitter(900_000L);
        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            emitterCompleted.set(true);
            emitter.complete();
            log.warn("SSE emitter timed out, appId={}", request.getAppId());
        });
        emitter.onCompletion(() -> {
            emitterCompleted.set(true);
        });
        emitter.onError(e -> {
            emitterCompleted.set(true);
            log.warn("SSE emitter error, appId={}: {}", request.getAppId(), e.getMessage());
        });

        aiWorkflowService.generate(request.getAppId(), request.getMessage())
            .subscribe(
                event -> {
                    if (emitterCompleted.get()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().data(event.data()));
                    } catch (IllegalStateException | IOException e) {
                        emitterCompleted.set(true);
                        log.warn("SSE send failed (client disconnected), appId={}: {}", request.getAppId(), e.getMessage());
                        try { emitter.complete(); } catch (Exception ignored) {}
                    }
                },
                error -> {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        log.error("Workflow stream error, appId={}", request.getAppId(), error);
                        try { emitter.completeWithError(error); } catch (Exception ignored) {}
                    }
                },
                () -> {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                }
            );

        return emitter;
    }

    @Operation(summary = "查询工作流状态")
    @GetMapping("/status")
    @AuthCheck
    public BaseResponse<WorkflowStatusResponse> getStatus(@RequestParam Long appId) {
        return ResultUtils.success(aiWorkflowService.getStatus(appId));
    }

    @Operation(summary = "断线重连 - 回放事件并实时订阅")
    @GetMapping(value = "/reconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck
    public SseEmitter reconnect(@RequestParam Long appId) {
        SseEmitter emitter = new SseEmitter(900_000L);
        AtomicBoolean emitterCompleted = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            emitterCompleted.set(true);
            emitter.complete();
            log.warn("重连SSE超时, appId={}", appId);
        });
        emitter.onCompletion(() -> emitterCompleted.set(true));
        emitter.onError(e -> {
            emitterCompleted.set(true);
            log.debug("重连SSE错误, appId={}: {}", appId, e.getMessage());
        });

        // delaySubscription(300ms) 确保 Spring MVC 先完成 emitter.initialize()，
        // 之后 boundedElastic 线程才开始回放事件，避免事件被缓冲到 initialSend。
        // /generate 不需要延迟是因为第一个事件要等 AI 模型响应（数秒），天然有延迟。
        AtomicInteger sendCounter = new AtomicInteger(0);

        Flux.<String>create(sink -> {
            if (emitterCompleted.get()) {
                sink.complete();
                return;
            }

            WorkflowStatusResponse status = aiWorkflowService.getStatus(appId);
            List<String> cachedEvents = aiWorkflowService.getCachedEvents(appId);
            int replayedCount = 0;

            // 1. 回放已缓存的事件（原样转发，不做合并）
            for (String payload : cachedEvents) {
                if (emitterCompleted.get()) {
                    sink.complete();
                    return;
                }
                sink.next(payload);
                replayedCount++;
            }

            log.info("SSE重连回放完成, appId={}, 事件数={}, 运行中={}",
                    appId, replayedCount, "running".equals(status.getStatus()));

            // 2. 如果工作流仍在运行，注册为订阅者接收实时事件
            if ("running".equals(status.getStatus())) {
                @SuppressWarnings("unchecked")
                final Consumer<String>[] holder = new Consumer[1];
                holder[0] = payload -> {
                    if (emitterCompleted.get()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().data(payload));
                        int liveCount = sendCounter.incrementAndGet();
                        if (liveCount <= 3) {
                            log.info("重连实时事件发送, appId={}, 实时事件计数={}", appId, liveCount);
                        }
                    } catch (IllegalStateException | IOException e) {
                        if (emitterCompleted.compareAndSet(false, true)) {
                            aiWorkflowService.unregisterReconnectSubscriber(appId, holder[0]);
                            try { emitter.complete(); } catch (Exception ignored) {}
                        }
                    }
                };
                Consumer<String> subscriber = holder[0];
                aiWorkflowService.registerReconnectSubscriber(appId, subscriber);

                Runnable cleanup = () -> {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        aiWorkflowService.unregisterReconnectSubscriber(appId, subscriber);
                    }
                };

                emitter.onCompletion(cleanup);
                emitter.onTimeout(() -> {
                    cleanup.run();
                    emitter.complete();
                    log.info("重连SSE超时(订阅中), appId={}", appId);
                });
                emitter.onError(e -> {
                    cleanup.run();
                    log.debug("重连SSE错误(订阅中), appId={}: {}", appId, e.getMessage());
                });
            } else {
                // 工作流已结束，回放后关闭
                sink.complete();
            }
        })
        .delaySubscription(Duration.ofMillis(300))
        .subscribeOn(Schedulers.boundedElastic())
        .subscribe(
            event -> {
                if (emitterCompleted.get()) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().data(event));
                    int count = sendCounter.incrementAndGet();
                    if (count <= 3) {
                        log.info("重连回放事件已发送, appId={}, 发送计数={}, 事件长度={}",
                                appId, count, event.length());
                    }
                } catch (IllegalStateException | IOException e) {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        log.warn("重连回放发送失败, appId={}: {}", appId, e.getMessage());
                        try { emitter.complete(); } catch (Exception ignored) {}
                    }
                }
            },
            error -> {
                if (emitterCompleted.compareAndSet(false, true)) {
                    log.error("重连Flux错误, appId={}", appId, error);
                    try { emitter.completeWithError(error); } catch (Exception ignored) {}
                }
            },
            () -> {
                int totalSent = sendCounter.get();
                log.info("重连回放Flux完成, appId={}, 总发送事件数={}", appId, totalSent);
                WorkflowStatusResponse status = aiWorkflowService.getStatus(appId);
                if (!"running".equals(status.getStatus())) {
                    if (emitterCompleted.compareAndSet(false, true)) {
                        emitter.complete();
                    }
                }
            }
        );

        return emitter;
    }

}
