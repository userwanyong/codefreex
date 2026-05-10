package cn.wanyj.codefreex.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * P7 SSE 浜嬩欢
 *
 * @author BanXia
 */
@Data
@AllArgsConstructor
public class WorkflowEvent {

    private String type;
    private Object data;
}
