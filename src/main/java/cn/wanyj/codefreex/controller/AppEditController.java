package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.service.AiWorkflowService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可视化编辑（已整合进工作流，通过意图识别自动路由）
 *
 * @author BanXia
 */
@Slf4j
@Tag(name = "可视化编辑")
@RestController
@RequestMapping("/app/edit")
@RequiredArgsConstructor
public class AppEditController {

    private final AiWorkflowService aiWorkflowService;

}
