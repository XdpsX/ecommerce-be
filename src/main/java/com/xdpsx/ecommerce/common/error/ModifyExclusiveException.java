package com.xdpsx.ecommerce.common.error;


public class ModifyExclusiveException extends APIException {
    public ModifyExclusiveException(String message, Object... args) {
        super(message, args);
    }

    public ModifyExclusiveException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
