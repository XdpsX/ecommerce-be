package com.xdpsx.ecommerce.exceptions;

import com.xdpsx.ecommerce.constants.messages.APIMessage;

public class DuplicateException extends APIException {
    public DuplicateException(String message, Object... args) {
        super(message, args);
    }

    public DuplicateException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
