package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.response.TagVO;
import cn.wanyj.codefreex.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签公开接口
 *
 * @author wanyj
 */
@Tag(name = "标签接口")
@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "获取所有预设标签")
    @GetMapping("/list")
    public BaseResponse<List<TagVO>> listAllTags() {
        return ResultUtils.success(tagService.listAllTags());
    }
}
