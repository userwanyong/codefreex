package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.service.AppStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 已部署应用访问控制器
 *
 * @author BanXia
 */
@Tag(name = "已部署应用访问接口")
@RestController
@RequestMapping("/deploy")
@RequiredArgsConstructor
public class DeployedAppController {

    private final AppStorageService appStorageService;

    @Operation(summary = "访问已部署应用静态资源")
    @GetMapping({"/{deployKey}", "/{deployKey}/", "/{deployKey}/**"})
    public ResponseEntity<Resource> deployed(@PathVariable String deployKey, HttpServletRequest request) throws IOException {
        String relativePath = AppPreviewController.extractRelativePath(request, deployKey, "/deploy/");
        Resource resource = appStorageService.loadDeployedResource(deployKey, relativePath);
        return AppPreviewController.buildResponse(resource);
    }
}
