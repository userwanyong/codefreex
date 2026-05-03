package cn.wanyj.codefreex.common;

import cn.wanyj.codefreex.exception.ResponseCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author wanyj
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ResponseCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}

