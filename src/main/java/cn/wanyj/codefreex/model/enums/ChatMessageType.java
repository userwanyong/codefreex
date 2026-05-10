package cn.wanyj.codefreex.model.enums;

import cn.wanyj.codefreex.exception.BusinessException;
import cn.wanyj.codefreex.exception.ResponseCode;

/**
 * 对话消息类型
 *
 * @author BanXia
 */
public enum ChatMessageType {

    USER("user"),
    AI("ai");

    private final String value;

    ChatMessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ChatMessageType fromValue(String value) {
        for (ChatMessageType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new BusinessException(ResponseCode.PARAMS_ERROR, "无效的消息类型");
    }
}
