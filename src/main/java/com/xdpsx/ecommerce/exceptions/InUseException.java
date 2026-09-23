package com.xdpsx.ecommerce.exceptions;

import com.xdpsx.ecommerce.constants.messages.APIMessage;

public class InUseException extends APIException {
    public InUseException(String message, Object... args) {
        super(message, args);
    }

    public InUseException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
