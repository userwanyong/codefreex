package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.AppConstant;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;
import cn.wanyj.codefreex.model.entity.App;
import cn.wanyj.codefreex.service.WorkflowFileToolService;
import cn.wanyj.codefreex.mapper.AppMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * 应用代码读取控制器
 *
 * @author BanXia
 */
@Tag(name = "应用代码接口")
@RestController
@RequestMapping("/app")
@RequiredArgsConstructor
public class AppCodeController {

    private final AppMapper appMapper;
    private final WorkflowFileToolService workflowFileToolService;

    @Operation(summary = "获取应用代码内容")
    @GetMapping("/{appId}/code")
    @AuthCheck
    public BaseResponse<String> getAppCode(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            throw new BusinessException(ResponseCode.NOT_FOUND_ERROR, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "无权访问该应用");
        }

        String deployKey = app.getDeployKey();
        if (deployKey == null || deployKey.isBlank()) {
            return ResultUtils.success("");
        }

        Path rootDir = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, deployKey);
        List<String> filePaths = workflowFileToolService.listFiles(rootDir);
        if (filePaths.isEmpty()) {
            return ResultUtils.success("");
        }

        StringBuilder sb = new StringBuilder();
        for (String filePath : filePaths) {
            String content = workflowFileToolService.readFile(rootDir, filePath);
            if (content == null || content.isBlank()) {
                continue;
            }

            String displayPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            String lang = inferLanguage(displayPath);

            sb.append("```").append(lang).append(':').append(displayPath).append('\n');
            sb.append(content);
            if (!content.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("```\n\n");
        }

        return ResultUtils.success(sb.toString().trim());
    }

    private String inferLanguage(String path) {
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx < 0) {
            return "plaintext";
        }
        String ext = path.substring(dotIdx + 1).toLowerCase();
        return switch (ext) {
            case "html", "htm" -> "html";
            case "css" -> "css";
            case "js", "mjs" -> "javascript";
            case "ts" -> "typescript";
            case "jsx" -> "javascript";
            case "tsx" -> "typescript";
            case "vue" -> "vue";
            case "json" -> "json";
            case "md" -> "markdown";
            case "py" -> "python";
            case "java" -> "java";
            case "xml", "svg" -> "xml";
            case "yml", "yaml" -> "yaml";
            case "sh", "bash" -> "bash";
            default -> "plaintext";
        };
    }
}
