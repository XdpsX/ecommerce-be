package com.xdpsx.ecommerce.common.error;


public class DuplicateException extends APIException {
    public DuplicateException(String message, Object... args) {
        super(message, args);
    }

    public DuplicateException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
