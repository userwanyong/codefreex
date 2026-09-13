package cn.wanyj.codefreex.model.dto.response;

import lombok.Data;

/**
 * 系统配置项视图（管理端）
 *
 * @author wanyj
 */
@Data
public class SystemConfigItemVO {

    /**
     * 配置键
     */
    private String key;

    /**
     * 配置项名称
     */
    private String label;

    /**
     * 当前值
     */
    private String value;

    /**
     * 值类型（STRING/INT/DOUBLE/BOOLEAN）
     */
    private String valueType;

    /**
     * 是否敏感信息
     */
    private Boolean sensitive;

    /**
     * 说明
     */
    private String description;

    /**
     * 默认值（播种入库时的初始值）
     */
    private String defaultValue;
}
