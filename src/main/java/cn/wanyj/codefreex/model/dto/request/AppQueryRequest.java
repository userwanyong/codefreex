package cn.wanyj.codefreex.model.dto.request;

import cn.wanyj.codefreex.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员查询应用请求
 *
 * @author wanyj
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppQueryRequest extends PageRequest {

    /**
     * 应用状态（可选）
     */
    private String status;

    /**
     * 应用名称（模糊搜索，可选）
     */
    private String appName;
}
