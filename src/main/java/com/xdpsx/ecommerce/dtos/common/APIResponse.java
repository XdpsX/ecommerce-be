package com.xdpsx.ecommerce.dtos.common;

import org.springframework.http.HttpStatus;

import com.xdpsx.ecommerce.constants.messages.APIMessage;
import com.xdpsx.ecommerce.constants.messages.SMessage;

import lombok.Getter;

@Getter
public class APIResponse<T> {
    private int status;
    private String message;
    private T data;

    public APIResponse() {}

    public APIResponse(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public APIResponse(HttpStatus httpStatus, String message, T data) {
        this.status = httpStatus.value();
        this.message = message;
        this.data = data;
    }

    public APIResponse(HttpStatus status, T data, APIMessage apiMessage) {
        this(status.value(), apiMessage.message(), data);
    }

    public static <T> APIResponse<T> ok(T data) {
        return new APIResponse<>(HttpStatus.OK, SMessage.SUCCESS.message(), data);
    }

    public static <T> APIResponse<T> ok(T data, APIMessage message) {
        return new APIResponse<>(HttpStatus.OK, message.message(), data);
    }

    public static <T> APIResponse<T> noContent() {
        return new APIResponse<>(HttpStatus.OK, SMessage.SUCCESS.message(), (T) null);
    }

    public static <T> APIResponse<T> noContent(APIMessage message) {
        return new APIResponse<>(HttpStatus.OK, message.message(), (T) null);
    }
}
