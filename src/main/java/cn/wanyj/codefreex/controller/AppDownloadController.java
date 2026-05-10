package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.UserContext;
import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.service.AppDeployService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 应用源码下载控制器
 *
 * @author BanXia
 */
@Tag(name = "应用下载接口")
@RestController
@RequestMapping("/app/download")
@RequiredArgsConstructor
public class AppDownloadController {

    private final AppDeployService appDeployService;

    @Operation(summary = "下载应用源码")
    @GetMapping("/{appId}")
    @AuthCheck
    public ResponseEntity<Resource> download(@PathVariable Long appId) {
        Long userId = UserContext.getLoginUserId();
        AppDeployService.DownloadArchive archive = appDeployService.prepareDownloadArchive(userId, appId);
        Resource resource = new FileSystemResource(archive.archivePath());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(archive.fileName(), StandardCharsets.UTF_8))
                .body(resource);
    }
}
