package cn.wanyj.codefreex.model.enums;

/**
 * 代码生成模式
 *
 * @author BanXia
 */
public enum CodeGenType {

    HTML("html"),
    MULTI_FILE("multi_file"),
    VUE("vue");

    private final String value;

    CodeGenType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CodeGenType normalize(String value) {
        if (value == null || value.isBlank()) {
            return HTML;
        }
        for (CodeGenType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return HTML;
    }
}
