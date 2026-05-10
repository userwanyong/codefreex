package cn.wanyj.codefreex.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多文件代码解析器
 *
 * @author BanXia
 */
public final class FileBundleParser {

    private static final Pattern FILE_BLOCK_PATTERN =
            Pattern.compile("```file:([^\\n\\r]+)\\R([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private FileBundleParser() {
    }

    public static Map<String, String> parse(String content) {
        Map<String, String> files = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(content);
        while (matcher.find()) {
            String fileName = matcher.group(1).trim();
            String fileContent = matcher.group(2).strip();
            files.put(fileName, fileContent);
        }
        return files;
    }
}
