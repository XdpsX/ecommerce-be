package com.xdpsx.ecommerce.exceptions;

import com.xdpsx.ecommerce.constants.messages.APIMessage;

public class BadRequestException extends APIException {
    public BadRequestException(String message, Object... args) {
        super(message, args);
    }

    public BadRequestException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
