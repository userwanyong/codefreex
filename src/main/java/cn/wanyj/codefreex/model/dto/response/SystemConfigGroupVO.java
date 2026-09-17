package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 系统配置分组视图（管理端）
 *
 * @author wanyj
 */
@Data
public class SystemConfigGroupVO {

    /**
     * 分组编码
     */
    private String group;

    /**
     * 分组名称
     */
    private String groupName;

    /**
     * 分组内配置项
     */
    private List<SystemConfigItemVO> items;
}
