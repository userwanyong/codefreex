package cn.wanyj.codefreex.controller;

import cn.wanyj.codefreex.auth.annotation.AuthCheck;
import cn.wanyj.codefreex.common.BaseResponse;
import cn.wanyj.codefreex.common.ResultUtils;
import cn.wanyj.codefreex.model.dto.response.TagVO;
import cn.wanyj.codefreex.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理接口（管理员）
 *
 * @author wanyj
 */
@Tag(name = "标签管理接口（管理员）")
@RestController
@RequestMapping("/tag/admin")
@RequiredArgsConstructor
public class TagAdminController {

    private final TagService tagService;

    @Operation(summary = "获取所有标签列表")
    @GetMapping("/list")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<List<TagVO>> listTags() {
        return ResultUtils.success(tagService.listAllTags());
    }

    @Operation(summary = "新建标签")
    @PostMapping("/create")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> createTag(@RequestParam String name,
                                           @RequestParam(required = false) Integer sortOrder) {
        tagService.createTag(name, sortOrder);
        return ResultUtils.success(true);
    }

    @Operation(summary = "编辑标签")
    @PostMapping("/update")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> updateTag(@RequestParam Long id,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) Integer sortOrder) {
        tagService.updateTag(id, name, sortOrder);
        return ResultUtils.success(true);
    }

    @Operation(summary = "删除标签")
    @PostMapping("/delete")
    @AuthCheck(roles = {"ROLE_ADMIN", "ROLE_PLATFORM_ADMIN"})
    public BaseResponse<Boolean> deleteTag(@RequestParam Long id) {
        tagService.deleteTag(id);
        return ResultUtils.success(true);
    }
}
