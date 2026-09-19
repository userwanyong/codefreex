package cn.wanyj.codefreex.service.impl;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具循环历史压缩器：控制同步模型单次请求体规模，避免推理型模型在大上下文下
 * prefill+推理时间膨胀直至触发 HTTP 超时。
 *
 * 消息格式约定（由 {@code AiWorkflowServiceImpl#runIterationToolLoop} 生成）：
 * <pre>
 * 工具执行结果:
 * 【readFile src/App.vue】
 * &lt;文件全文&gt;
 *
 * 【editFile src/App.vue】
 * &lt;修改结果&gt;
 * </pre>
 *
 * 压缩分两阶段，按信息价值由高到低处理：
 * 1. 过期失效（零信息损失）：被后续 writeFile/editFile/deleteFile 修改过的文件，
 *    其更早的 readFile 结果已不反映磁盘现状，属于无效历史，整块替换为过期标记；
 * 2. 块级截断（保留元信息）：总量仍超阈值时，较早轮次的工具结果按块截断，
 *    每块保留块头（工具名+文件路径）与内容头部，文件当前内容以磁盘为准，
 *    模型需要完整内容时可重新 readFile。
 *
 * @author wanyj
 */
@Slf4j
final class ToolHistoryCompactor {

    /** 用户消息总量超过该字符数后启动块级截断 */
    private static final int HISTORY_CHAR_LIMIT = 40_000;
    /** 块级截断时完整保留的最近工具结果消息条数（轮次） */
    private static final int KEEP_RECENT_MESSAGES = 2;
    /** 早期轮次每个工具块保留的内容头部字符数 */
    private static final int BLOCK_KEEP_HEAD_CHARS = 800;
    /** 单个工具块允许的最大字符数（保留轮次中的超大文件也截断到此长度） */
    private static final int BLOCK_MAX_CHARS = 12_000;

    private static final String TOOL_RESULT_PREFIX = "工具执行结果:";
    /** 块头格式：【工具名 相对路径】；限定已知工具名，避免误切分文件内容中的【】文本 */
    private static final Pattern BLOCK_HEADER =
            Pattern.compile("【(listFiles|readFile|writeFile|editFile|deleteFile)(?:\\s+([^】\\n]+))?】");
    private static final Pattern READ_BLOCK_HEADER =
            Pattern.compile("【readFile\\s+([^】\\n]+)】");
    private static final Pattern WRITE_BLOCK_HEADER =
            Pattern.compile("【(?:writeFile|editFile|deleteFile)\\s+([^】\\n]+)】");

    private static final String STALE_MARK =
            "（此文件已在其后被修改，读取内容已过期省略；当前内容以磁盘为准，如需查看请重新调用 readFile）";
    private static final String TRUNCATE_MARK =
            "\n…（内容过长已截断，文件路径见本块开头；如需完整内容请重新调用 readFile）";

    private ToolHistoryCompactor() {
    }

    /**
     * 就地压缩消息列表中的历史工具结果。
     *
     * @return 压缩后的总字符数（未触发压缩时返回 -1）
     */
    static long compact(List<ChatMessage> messages) {
        int expiredMessages = expireStaleReadResults(messages);
        int truncatedMessages = truncateOldBlocks(messages);
        if (expiredMessages == 0 && truncatedMessages == 0) {
            return -1;
        }
        long totalChars = totalChars(messages);
        log.info("[Workflow] 工具循环历史已压缩: 失效消息={}, 截断消息={}, 压缩后总量={}字符",
                expiredMessages, truncatedMessages, totalChars);
        return totalChars;
    }

    /**
     * 阶段一：过期失效。按时间倒序扫描，writeFile/editFile/deleteFile 命中的文件路径
     * 记入已修改集合，使更早出现的 readFile 全文块替换为过期标记；
     * 同一条消息内按块序生效（先读后改，读到的旧内容同样过期）。
     */
    private static int expireStaleReadResults(List<ChatMessage> messages) {
        Set<String> modifiedFiles = new HashSet<>();
        int expiredMessages = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!(messages.get(i) instanceof UserMessage um)) {
                continue;
            }
            String text = um.singleText();
            if (!text.startsWith(TOOL_RESULT_PREFIX)) {
                continue;
            }
            String rewritten = expireInMessage(text, modifiedFiles);
            if (rewritten != null) {
                messages.set(i, UserMessage.from(rewritten));
                expiredMessages++;
            }
            // 本消息中的写操作对更早的消息同样生效
            Matcher write = WRITE_BLOCK_HEADER.matcher(text);
            while (write.find()) {
                modifiedFiles.add(write.group(1).trim());
            }
        }
        return expiredMessages;
    }

    /**
     * 阶段二：块级截断。总量超阈值时，除最近 {@link #KEEP_RECENT_MESSAGES} 条工具结果消息外，
     * 每个块的内容截断到 {@link #BLOCK_KEEP_HEAD_CHARS}；若总量仍超阈值，
     * 保留消息中的超大块再截断到 {@link #BLOCK_MAX_CHARS}。
     */
    private static int truncateOldBlocks(List<ChatMessage> messages) {
        if (totalChars(messages) <= HISTORY_CHAR_LIMIT) {
            return 0;
        }
        List<Integer> toolResultIdx = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) instanceof UserMessage um
                    && um.singleText().startsWith(TOOL_RESULT_PREFIX)) {
                toolResultIdx.add(i);
            }
        }
        if (toolResultIdx.isEmpty()) {
            return 0;
        }
        int keepFrom = Math.max(0, toolResultIdx.size() - KEEP_RECENT_MESSAGES);
        int truncated = truncateRange(messages, toolResultIdx, 0, keepFrom, BLOCK_KEEP_HEAD_CHARS);
        if (totalChars(messages) > HISTORY_CHAR_LIMIT) {
            truncated += truncateRange(messages, toolResultIdx, keepFrom, toolResultIdx.size(), BLOCK_MAX_CHARS);
        }
        return truncated;
    }

    /** 对 [from, to) 范围内的工具结果消息做块体截断，返回发生截断的消息数 */
    private static int truncateRange(List<ChatMessage> messages, List<Integer> idxList,
                                     int from, int to, int maxBodyChars) {
        int truncated = 0;
        for (int seq = from; seq < to; seq++) {
            int idx = idxList.get(seq);
            UserMessage um = (UserMessage) messages.get(idx);
            String text = um.singleText();
            String rewritten = truncateBlocks(text, maxBodyChars);
            if (rewritten != null) {
                messages.set(idx, UserMessage.from(rewritten));
                truncated++;
            }
        }
        return truncated;
    }

    /**
     * 消息内的过期失效：块按时间正序排列，倒序遍历使较晚的写操作先进入局部已修改集合，
     * 从而标记更早的 readFile 块；最后正序重组文本。没有任何变化时返回 null。
     */
    private static String expireInMessage(String text, Set<String> globalModified) {
        List<int[]> headerRanges = findBlockHeaders(text);
        if (headerRanges.isEmpty()) {
            return null;
        }
        Set<String> localModified = new HashSet<>(globalModified);
        String[] newBodies = new String[headerRanges.size()];
        for (int b = headerRanges.size() - 1; b >= 0; b--) {
            String header = text.substring(headerRanges.get(b)[0], headerRanges.get(b)[1]);
            Matcher write = WRITE_BLOCK_HEADER.matcher(header);
            if (write.find()) {
                localModified.add(write.group(1).trim());
                continue;
            }
            Matcher read = READ_BLOCK_HEADER.matcher(header);
            if (read.find() && localModified.contains(read.group(1).trim())) {
                newBodies[b] = STALE_MARK;
            }
        }
        return rebuild(text, headerRanges, newBodies);
    }

    /** 消息内的块体截断：超过 maxBodyChars 的块体保留头部并追加截断提示 */
    private static String truncateBlocks(String text, int maxBodyChars) {
        List<int[]> headerRanges = findBlockHeaders(text);
        if (headerRanges.isEmpty()) {
            return null;
        }
        String[] newBodies = new String[headerRanges.size()];
        for (int b = 0; b < headerRanges.size(); b++) {
            int bodyStart = headerRanges.get(b)[1];
            int bodyEnd = b + 1 < headerRanges.size() ? headerRanges.get(b + 1)[0] : text.length();
            String body = text.substring(bodyStart, bodyEnd);
            if (body.length() > maxBodyChars) {
                newBodies[b] = body.substring(0, maxBodyChars) + TRUNCATE_MARK;
            }
        }
        return rebuild(text, headerRanges, newBodies);
    }

    /** 定位全部工具块头位置：{headerStart, headerEnd} */
    private static List<int[]> findBlockHeaders(String text) {
        Matcher header = BLOCK_HEADER.matcher(text);
        List<int[]> ranges = new ArrayList<>();
        while (header.find()) {
            ranges.add(new int[]{header.start(), header.end()});
        }
        return ranges;
    }

    /** 用 newBoids 中非 null 的块体替换原块体重组文本；无任何替换时返回 null */
    private static String rebuild(String text, List<int[]> headerRanges, String[] newBodies) {
        boolean changed = false;
        for (String body : newBodies) {
            if (body != null) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return null;
        }
        StringBuilder out = new StringBuilder(text.length() / 2);
        int copied = 0;
        for (int b = 0; b < headerRanges.size(); b++) {
            int blockStart = headerRanges.get(b)[0];
            int blockEnd = b + 1 < headerRanges.size() ? headerRanges.get(b + 1)[0] : text.length();
            out.append(text, copied, blockStart);
            if (newBodies[b] != null) {
                out.append(text, blockStart, headerRanges.get(b)[1]).append(newBodies[b]);
            } else {
                out.append(text, blockStart, blockEnd);
            }
            copied = blockEnd;
        }
        out.append(text, copied, text.length());
        return out.toString();
    }

    private static long totalChars(List<ChatMessage> messages) {
        long total = 0;
        for (ChatMessage m : messages) {
            if (m instanceof UserMessage um) {
                total += um.singleText().length();
            }
        }
        return total;
    }
}
