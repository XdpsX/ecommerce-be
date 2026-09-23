package com.xdpsx.ecommerce.common.error;

public class BadRequestException extends APIException {
    public BadRequestException(String message, Object... args) {
        super(message, args);
    }

    public BadRequestException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
