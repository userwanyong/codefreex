package cn.wanyj.codefreex.service.tools;

import cn.wanyj.codefreex.service.WorkflowFileToolService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.nio.file.Path;
import java.util.List;

/**
 * 工作流文件操作工具 - 供 AI 自主调用
 *
 * @author wanyj
 */
public class WorkflowFileTools {

    private final Path rootDir;
    private final WorkflowFileToolService fileToolService;

    public WorkflowFileTools(Path rootDir, WorkflowFileToolService fileToolService) {
        this.rootDir = rootDir;
        this.fileToolService = fileToolService;
    }

    @Tool("列出项目中的所有文件路径。在读取或修改文件之前，先调用此工具了解项目结构。")
    public List<String> listFiles() {
        return fileToolService.listFiles(rootDir);
    }

    @Tool("读取指定文件的完整内容。使用相对路径，如 'src/App.vue' 或 'index.html'。")
    public String readFile(@P("相对于项目根目录的文件路径") String relativePath) {
        return fileToolService.readFile(rootDir, relativePath);
    }

    @Tool("创建或覆盖写入一个文件。如果文件已存在会被完全替换。")
    public String writeFile(@P("相对于项目根目录的文件路径") String relativePath,
                            @P("要写入的完整文件内容") String content) {
        fileToolService.writeFile(rootDir, relativePath, content);
        return "文件写入成功: " + relativePath;
    }

    @Tool("编辑文件中的一部分内容，通过查找原始文本并替换为新文本。适合局部修改，不会影响文件其他部分。")
    public String editFile(@P("相对于项目根目录的文件路径") String relativePath,
                          @P("要被替换的原始文本（必须与文件中的内容完全匹配）") String originalContent,
                          @P("替换后的新文本") String newContent) {
        fileToolService.editFile(rootDir, relativePath, originalContent, newContent);
        return "文件修改成功: " + relativePath;
    }

    @Tool("删除指定的文件。")
    public String deleteFile(@P("相对于项目根目录的文件路径") String relativePath) {
        fileToolService.deleteFile(rootDir, relativePath);
        return "文件已删除: " + relativePath;
    }
}
