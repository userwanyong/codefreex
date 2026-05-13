package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 码点来源类型
 *
 * @author wanyj
 */
@Getter
@AllArgsConstructor
public enum CreditSourceType {

    REDEEM("redeem", "兑换码"),
    AI_CHAT("ai_chat", "AI对话"),
    ADMIN("admin", "管理员操作"),
    REGISTER_GIFT("register_gift", "注册赠送");

    private final String value;
    private final String desc;

    public static CreditSourceType fromValue(String value) {
        for (CreditSourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的来源类型: " + value);
    }
}
