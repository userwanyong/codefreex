package cn.wanyj.codefreex.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

/**
 * 系统配置批量更新请求（管理端）
 *
 * @author wanyj
 */
@Data
public class SystemConfigUpdateRequest {

    /**
     * 配置键 -> 配置值
     */
    @NotEmpty
    private Map<String, String> configs;
}
