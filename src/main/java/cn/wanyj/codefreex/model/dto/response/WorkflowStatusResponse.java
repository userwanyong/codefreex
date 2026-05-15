package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流状态
 *
 * @author BanXia
 */
@Data
public class WorkflowStatusResponse {

    private Long appId;
    private String status;
    private String currentNode;
    private String route;
    private Integer retryCount;
    private String message;
    private LocalDateTime updateTime;

    /** 缓存的事件总数（用于前端判断是否有可回放的事件） */
    private Integer cachedEventCount;
}
