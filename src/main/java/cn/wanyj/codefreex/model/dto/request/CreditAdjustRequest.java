package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员调整码点请求
 *
 * @author wanyj
 */
@Data
public class CreditAdjustRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "调整数量不能为空")
    private Integer amount;

    private String description;
}
