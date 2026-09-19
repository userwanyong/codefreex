package cn.wanyj.codefreex.service.impl;

import org.bsc.langgraph4j.StateGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 节点守卫回归测试：langgraph4j 阻塞迭代桥接（InternalIterator 预取 + Data.error）
 * 在节点异常后会再次调用 next() 导致失败节点被静默重复执行。
 * guardNode 将异常捕获到 nodeFailure（返回空状态更新），由消费循环（同 generate()）
 * 检测后立即终止，保证节点恰好执行一次且异常正常抛出。
 */
class WorkflowNodeGuardTest {

    @Test
    void guardedNode_syncThrow_executesExactlyOnceAndLoopFails() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<Throwable> nodeFailure = new AtomicReference<>();

        StateGraph<AiWorkflowServiceImpl.WorkflowGraphState> graph = new StateGraph<>(AiWorkflowServiceImpl.WorkflowGraphState::new)
                .addNode("ok", AiWorkflowServiceImpl.guardNode(
                        state -> CompletableFuture.completedFuture(Map.of("step", "ok")), nodeFailure))
                .addNode("boom", AiWorkflowServiceImpl.guardNode(
                        state -> CompletableFuture.completedFuture(boomSync(executions)), nodeFailure))
                .addEdge(StateGraph.START, "ok")
                .addEdge("ok", "boom")
                .addEdge("boom", StateGraph.END);
        var compiled = graph.compile();

        assertThatThrownBy(() -> {
            for (var output : compiled.stream(Map.of())) {
                // 与 generate() 主循环一致的失败检测
                Throwable nodeError = nodeFailure.get();
                if (nodeError != null) {
                    throw new RuntimeException("工作流节点执行失败(" + output.node() + "): "
                            + nodeError.getMessage(), nodeError);
                }
            }
            fail("节点异常未被主循环拦截");
        })
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("工作流节点执行失败")
                .hasMessageContaining("request timed out")
                .hasRootCauseInstanceOf(RuntimeException.class);

        // 关键断言：失败节点恰好执行一次（修复前 langgraph4j 桥接会重复执行）
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void guardedNode_asyncFutureFailure_capturedAndLoopFails() throws Exception {
        AtomicReference<Throwable> nodeFailure = new AtomicReference<>();

        StateGraph<AiWorkflowServiceImpl.WorkflowGraphState> graph = new StateGraph<>(AiWorkflowServiceImpl.WorkflowGraphState::new)
                .addNode("boom", AiWorkflowServiceImpl.guardNode(
                        state -> CompletableFuture.failedFuture(new RuntimeException("async model error")), nodeFailure))
                .addEdge(StateGraph.START, "boom")
                .addEdge("boom", StateGraph.END);
        var compiled = graph.compile();

        assertThatThrownBy(() -> {
            for (var output : compiled.stream(Map.of())) {
                Throwable nodeError = nodeFailure.get();
                if (nodeError != null) {
                    throw new RuntimeException("工作流节点执行失败: " + nodeError.getMessage(), nodeError);
                }
            }
            fail("异步异常未被主循环拦截");
        }).hasMessageContaining("async model error");
    }

    @Test
    void guardedNode_normalAction_passesThroughState() throws Exception {
        AtomicReference<Throwable> nodeFailure = new AtomicReference<>();

        StateGraph<AiWorkflowServiceImpl.WorkflowGraphState> graph = new StateGraph<>(AiWorkflowServiceImpl.WorkflowGraphState::new)
                .addNode("ok", AiWorkflowServiceImpl.guardNode(
                        state -> CompletableFuture.completedFuture(Map.of("step", "done")), nodeFailure))
                .addEdge(StateGraph.START, "ok")
                .addEdge("ok", StateGraph.END);
        var compiled = graph.compile();

        var it = compiled.stream(Map.of()).iterator();
        boolean stepSeen = false;
        int guard = 0;
        while (it.hasNext() && guard++ < 10) {
            var output = it.next();
            if ("done".equals(output.state().<String>value("step").orElse(""))) {
                stepSeen = true;
            }
        }
        assertThat(stepSeen).as("正常节点的状态更新应透传").isTrue();
        assertThat(nodeFailure.get()).isNull();
    }

    private Map<String, Object> boomSync(AtomicInteger executions) {
        executions.incrementAndGet();
        throw new RuntimeException("request timed out");
    }
}
