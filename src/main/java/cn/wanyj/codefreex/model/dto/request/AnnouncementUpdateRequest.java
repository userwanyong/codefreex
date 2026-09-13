package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告更新请求（管理端）
 *
 * @author wanyj
 */
@Data
public class AnnouncementUpdateRequest {

    /**
     * 公告 id
     */
    @NotNull
    private Long id;

    /**
     * 公告标题
     */
    @NotBlank
    @Size(max = 128)
    private String title;

    /**
     * 公告内容（Markdown）
     */
    private String content;

    /**
     * 状态（draft-草稿/published-已发布/offline-已下线）
     */
    private String status;
}
