package cn.wanyj.codefreex.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 码点流水类型
 *
 * @author wanyj
 */
@Getter
@AllArgsConstructor
public enum CreditTransactionType {

    RECHARGE("recharge", "充值"),
    CONSUME("consume", "消费"),
    ADMIN_ADJUST("admin_adjust", "管理员调整"),
    GIFT("gift", "系统赠送");

    private final String value;
    private final String desc;

    public static CreditTransactionType fromValue(String value) {
        for (CreditTransactionType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的流水类型: " + value);
    }
}
