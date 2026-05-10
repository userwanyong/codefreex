package cn.wanyj.codefreex.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * P7 SSE 事件
 *
 * @author BanXia
 */
@Data
@AllArgsConstructor
public class WorkflowEvent {

    private String type;
    private Object data;
}
