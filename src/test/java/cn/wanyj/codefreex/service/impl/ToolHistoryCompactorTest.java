package cn.wanyj.codefreex.service.impl;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具循环历史压缩器单测：验证过期失效（零损失）与块级截断（保元信息）两阶段行为。
 */
class ToolHistoryCompactorTest {

    private static final String RESULT_PREFIX = "工具执行结果:";

    private static UserMessage toolResult(String blocks) {
        return UserMessage.from(RESULT_PREFIX + "\n" + blocks);
    }

    @Test
    void compact_expiredReadResult_replacedWithStaleMark() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                SystemMessage.from("system"),
                UserMessage.from("需求"),
                toolResult("【readFile src/App.vue】\n旧版本文件内容A\n\n"),
                AiMessage.from("已执行工具: readFile src/App.vue "),
                toolResult("【editFile src/App.vue】\n修改成功\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String text = ((UserMessage) messages.get(2)).singleText();
        assertThat(text).contains("已过期省略");
        assertThat(text).doesNotContain("旧版本文件内容A");
        // 编辑结果不受影响
        assertThat(((UserMessage) messages.get(4)).singleText()).contains("修改成功");
    }

    @Test
    void compact_unmodifiedReadResult_keptIntact() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【readFile src/App.vue】\n内容A\n\n【readFile src/style.css】\n内容B\n\n"),
                AiMessage.from("已执行工具: readFile src/App.vue readFile src/style.css "),
                toolResult("【editFile src/App.vue】\n修改成功\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String text = ((UserMessage) messages.get(1)).singleText();
        // App.vue 已被修改 → 过期；style.css 未被修改 → 保留
        assertThat(text).contains("已过期省略");
        assertThat(text).contains("内容B");
        assertThat(text).doesNotContain("内容A\n");
    }

    @Test
    void compact_readAfterEdit_notExpired() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【editFile src/App.vue】\n修改成功\n\n"),
                AiMessage.from("已执行工具: editFile src/App.vue "),
                toolResult("【readFile src/App.vue】\n修改后的最新内容\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        // readFile 发生在 editFile 之后，反映磁盘现状，不应过期
        assertThat(((UserMessage) messages.get(3)).singleText()).contains("修改后的最新内容");
    }

    @Test
    void compact_sameMessage_readBeforeWriteExpires() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【readFile src/App.vue】\n读取在前的内容\n\n【editFile src/App.vue】\n修改成功\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String text = ((UserMessage) messages.get(1)).singleText();
        assertThat(text).contains("已过期省略");
        assertThat(text).doesNotContain("读取在前的内容");
        assertThat(text).contains("修改成功");
    }

    @Test
    void compact_underLimit_noChange() {
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【readFile src/App.vue】\n内容A\n\n"),
                AiMessage.from("已执行工具: readFile src/App.vue ")
        ));

        long result = ToolHistoryCompactor.compact(messages);

        assertThat(result).isEqualTo(-1L);
        assertThat(((UserMessage) messages.get(1)).singleText()).contains("内容A");
    }

    @Test
    void compact_overLimit_oldBlocksTruncated_headerKept() {
        // 3 条工具结果消息：第 1 条早期（超大 readFile，被截断），第 2 条属保留轮次
        // 且截断早期后总量已低于阈值，应完整保留；第 3 条为最近轮次完整保留
        String bigContent = "X".repeat(20_000);
        List<ChatMessage> messages = new ArrayList<>(List.of(
                SystemMessage.from("system"),
                UserMessage.from("需求"),
                toolResult("【readFile src/big1.vue】\n" + bigContent + "\n\n"),
                AiMessage.from("已执行工具: readFile src/big1.vue "),
                toolResult("【readFile src/big2.vue】\n" + bigContent + "\n\n"),
                AiMessage.from("已执行工具: readFile src/big2.vue "),
                toolResult("【readFile src/recent.vue】\n最新内容\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String old1 = ((UserMessage) messages.get(2)).singleText();
        String keep = ((UserMessage) messages.get(4)).singleText();
        String recent = ((UserMessage) messages.get(6)).singleText();
        // 早期块：路径保留在块头，内容被截断
        assertThat(old1).contains("【readFile src/big1.vue】");
        assertThat(old1).doesNotContain(bigContent);
        // 保留轮次与最近轮次完整保留
        assertThat(keep).contains(bigContent);
        assertThat(recent).contains("最新内容");
        // 总量显著下降（原始约 40K，截断早期后约 21K）
        long total = messages.stream()
                .filter(m -> m instanceof UserMessage)
                .mapToLong(m -> ((UserMessage) m).singleText().length())
                .sum();
        assertThat(total).isLessThan(22_000);
    }

    @Test
    void compact_recentOversizeBlock_truncatedToMax() {
        // 只有 1 条工具结果消息（受保留保护），但单块超大导致总量超阈值，仍截到块上限
        String bigContent = "Y".repeat(45_000);
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【readFile src/huge.vue】\n" + bigContent + "\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String text = ((UserMessage) messages.get(1)).singleText();
        assertThat(text).contains("【readFile src/huge.vue】");
        assertThat(text).doesNotContain(bigContent);
        assertThat(text.length()).isLessThan(15_000);
    }

    @Test
    void compact_bracketTextInContent_notSplitAsBlock() {
        // 文件内容中的【note】样式文本不应被识别为工具块头
        String tricky = "正文包含【note】和【listFiles src/fake】的文本\n";
        List<ChatMessage> messages = new ArrayList<>(List.of(
                UserMessage.from("需求"),
                toolResult("【readFile src/App.vue】\n" + tricky + "\n\n"),
                AiMessage.from("已执行工具: readFile src/App.vue "),
                toolResult("【editFile src/App.vue】\n修改成功\n\n")
        ));

        ToolHistoryCompactor.compact(messages);

        String text = ((UserMessage) messages.get(1)).singleText();
        // 真实 readFile 块已过期，伪造块头不影响处理
        assertThat(text).contains("已过期省略");
        assertThat(((UserMessage) messages.get(3)).singleText()).contains("修改成功");
    }
}
