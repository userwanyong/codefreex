package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.service.AppStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 应用预览静态资源控制器
 *
 * @author BanXia
 */
@Tag(name = "应用预览接口")
@RestController
@RequestMapping("/static")
@RequiredArgsConstructor
public class AppPreviewController {

    private final AppStorageService appStorageService;

    @Operation(summary = "访问生成目录下的静态资源")
    @GetMapping({"/{deployKey}", "/{deployKey}/", "/{deployKey}/**"})
    public ResponseEntity<Resource> preview(@PathVariable String deployKey, HttpServletRequest request) throws IOException {
        String relativePath = extractRelativePath(request, deployKey, "/static/");
        Resource resource = appStorageService.loadGeneratedResource(deployKey, relativePath);
        return buildResponse(resource);
    }

    static ResponseEntity<Resource> buildResponse(Resource resource) throws IOException {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        Path path = resource.getFile().toPath();
        String contentType = Files.probeContentType(path);
        if (contentType != null) {
            mediaType = MediaType.parseMediaType(contentType);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(mediaType)
                .body(resource);
    }

    static String extractRelativePath(HttpServletRequest request, String deployKey, String prefix) {
        String requestUri = request.getRequestURI();
        String marker = prefix + deployKey;
        int start = requestUri.indexOf(marker);
        if (start < 0) {
            return "";
        }
        String relative = requestUri.substring(start + marker.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }
}
