package cn.wanyj.codefreex.model.dto.request;

import cn.wanyj.codefreex.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询请求（管理员，数据来源 auth-service）
 *
 * @author wanyj
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest {

    /**
     * 模糊搜索账号或邮箱
     */
    private String searchKey;

    /**
     * 筛选状态（1-正常，0-禁用）
     */
    private Integer status;
}
