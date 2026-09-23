package com.xdpsx.ecommerce.exceptions;

import com.xdpsx.ecommerce.constants.messages.APIMessage;

public class InvalidResourceTypeException extends APIException {
    public InvalidResourceTypeException(String message, Object... args) {
        super(message, args);
    }

    public InvalidResourceTypeException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
