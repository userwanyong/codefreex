package cn.wanyj.codefreex.model.dto.request;

import cn.wanyj.codefreex.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询请求（管理员）
 *
 * @author wanyj
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest {

    /**
     * 模糊搜索昵称
     */
    private String searchKey;

    /**
     * 筛选状态（active/disabled）
     */
    private String status;
}
