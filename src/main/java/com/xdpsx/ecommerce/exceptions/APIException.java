package com.xdpsx.ecommerce.exceptions;

import lombok.Getter;

@Getter
public abstract class APIException extends RuntimeException {
    private final Object[] args;

    public APIException(String message, Object[] args) {
        super(message);
        this.args = args;
    }
}
