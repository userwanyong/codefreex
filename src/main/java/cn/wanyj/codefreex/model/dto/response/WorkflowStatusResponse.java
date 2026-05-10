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
}
