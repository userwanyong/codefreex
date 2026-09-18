package cn.wanyj.codefreex.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器
 *
 * 文件块协议：开始围栏为 3 或 4 个反引号紧接 file:文件名，结束围栏为与开始等长且独立的反引号串。
 * 提示词约定模型输出四反引号围栏：文件内容（如嵌入 JS 字符串的 Markdown 文章）常含三反引号代码块，
 * 三反引号围栏会被内容提前截断导致文件残缺；四反引号围栏不受内容中三反引号影响。
 * 三反引号解析仅作兼容保留，模型偶发按旧格式输出时行为不劣于升级前。
 *
 * @author BanXia
 */
public final class FileBundleParser {

    static final Pattern FILE_BLOCK_PATTERN =
            Pattern.compile("(?<!`)(?<fence>`{3,4})file:(?<name>[^\\n\\r]+)\\R(?<body>[\\s\\S]*?)(?<!`)\\k<fence>(?!`)", Pattern.CASE_INSENSITIVE);

    private FileBundleParser() {
    }

    public static Map<String, String> parse(String content) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            String fileName = matcher.group("name").trim();
            String fileContent = matcher.group("body").strip();
            files.put(fileName, fileContent);
        }
        return files;
    }
}
