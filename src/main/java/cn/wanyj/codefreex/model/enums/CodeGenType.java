package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 代码生成类型枚举
 *
 * @author wanyj
 */
@Getter
@AllArgsConstructor
public enum CodeGenType {

    HTML("html", "单文件HTML"),
    MULTI_FILE("multi_file", "多文件项目"),
    VUE_PROJECT("vue_project", "Vue工程项目");

    private final String value;
    private final String desc;

    public static CodeGenType fromValue(String value) {
        for (CodeGenType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的代码生成类型: " + value);
    }
}
