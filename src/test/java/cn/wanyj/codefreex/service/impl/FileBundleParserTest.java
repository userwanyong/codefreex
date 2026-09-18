package cn.wanyj.codefreex.service.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author BanXia
 */
class FileBundleParserTest {

    @Test
    void parse_fourBacktickBlock_contentWithInnerThreeBackticksNotTruncated() {
        // 复刻线上故障场景：博客类站点把 Markdown 文章嵌在 JS 字符串里，
        // 文章正文含三反引号代码围栏，旧三反引号协议会在内层围栏处截断文件
        String article = "content: '## Vue3 入门\\n\\n示例代码：\\n\\n```js\\nconst x = ref(1)\\n```\\n\\n结束'"
                .replace("\\n", "\n");
        String content = """
                生成计划：一个博客站点

                ````file:src/utils/store.js
                export const posts = [
                  {
                %s
                  }
                ]
                ````

                生成完毕
                """.formatted(article);

        Map<String, String> files = FileBundleParser.parse(content);

        assertThat(files).containsOnlyKeys("src/utils/store.js");
        assertThat(files.get("src/utils/store.js")).contains("```js").contains("const x = ref(1)").contains("结束'");
    }

    @Test
    void parse_fourBacktickBlock_multipleFilesParsedFully() {
        String content = """
                ````file:index.html
                <!DOCTYPE html>
                ````

                ````file:src/main.js
                import { createApp } from 'vue'
                ````

                说明文字中间隔着也没关系

                ````file:style.css
                body { margin: 0 }
                ````
                """;

        Map<String, String> files = FileBundleParser.parse(content);

        assertThat(files).containsOnlyKeys("index.html", "src/main.js", "style.css");
        assertThat(files.get("index.html")).isEqualTo("<!DOCTYPE html>");
        assertThat(files.get("src/main.js")).isEqualTo("import { createApp } from 'vue'");
        assertThat(files.get("style.css")).isEqualTo("body { margin: 0 }");
    }

    @Test
    void parse_legacyThreeBacktickBlock_stillSupported() {
        String content = """
                ```file:script.js
                console.log('hi')
                ```
                """;

        Map<String, String> files = FileBundleParser.parse(content);

        assertThat(files).containsOnlyKeys("script.js");
        assertThat(files.get("script.js")).isEqualTo("console.log('hi')");
    }

    @Test
    void parse_legacyBlock_truncatedAtInnerFenceAsBefore() {
        // 兼容路径保留旧行为：三反引号块仍会被内层三反引号截断（仅模型违背四反引号约定时才会走到）
        String content = """
                ```file:script.js
                const md = `
                ```js
                code
                ```
                `
                ```
                """;

        Map<String, String> files = FileBundleParser.parse(content);

        assertThat(files).containsOnlyKeys("script.js");
        assertThat(files.get("script.js")).doesNotContain("code");
    }

    @Test
    void parse_unclosedBlock_notParsed() {
        // 流式生成中间态：块未闭合时不应解析出半截文件
        String content = """
                ````file:src/App.vue
                <template>
                  <div>写到一半
                """;

        assertThat(FileBundleParser.parse(content)).isEmpty();
    }

    @Test
    void parse_openWithFourCannotBeClosedByThree() {
        // 开始围栏四个反引号时，内层三反引号围栏不能作为结束
        String content = """
                ````file:README.md
                # 标题

                ```
                inner fence
                ```

                正文继续
                ````
                """;

        Map<String, String> files = FileBundleParser.parse(content);

        assertThat(files).containsOnlyKeys("README.md");
        assertThat(files.get("README.md")).contains("inner fence").contains("正文继续");
    }
}
