package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wanyj
 */

@Getter
@AllArgsConstructor
public enum InviteStatus {

    UNUSED("unused", "未使用"),
    PARTIAL("partial", "部分使用"),
    USED("used", "已用完"),
    EXPIRED("expired", "已过期"),
    DISABLED("disabled", "已禁用");

    private final String value;
    private final String desc;

    public static InviteStatus fromValue(String value) {
        for (InviteStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的邀请码状态: " + value);
    }
}
