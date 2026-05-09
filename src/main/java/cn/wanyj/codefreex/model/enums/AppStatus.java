package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用状态枚举
 *
 * @author wanyj
 */
@Getter
@AllArgsConstructor
public enum AppStatus {

    DRAFT("draft", "草稿"),
    GENERATING("generating", "生成中"),
    GENERATED("generated", "已生成"),
    DEPLOYED("deployed", "已部署"),
    DISABLED("disabled", "已禁用");

    private final String value;
    private final String desc;

    public static AppStatus fromValue(String value) {
        for (AppStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的应用状态: " + value);
    }
}
