package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用视图对象（脱敏，用于精选列表/公开查看）
 *
 * @author wanyj
 */
@Data
public class AppVO {

    private Long id;

    private String appName;

    private String description;

    private String cover;

    private String initPrompt;

    private LocalDateTime deployedTime;

    private String codeGenType;

    private String status;

    private String deployKey;

    private Integer isPublic;

    private Integer isFeatured;

    private Integer priority;

    private Integer viewCount;

    private Integer likeCount;

    private List<String> tags;

    private Long userId;

    private String userName;

    private String userAvatar;

    private LocalDateTime editTime;

    private LocalDateTime createTime;
}
