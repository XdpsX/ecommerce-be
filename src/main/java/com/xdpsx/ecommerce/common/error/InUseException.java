package com.xdpsx.ecommerce.common.error;

public class InUseException extends APIException {
    public InUseException(String message, Object... args) {
        super(message, args);
    }

    public InUseException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
