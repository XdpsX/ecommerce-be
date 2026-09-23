package com.xdpsx.ecommerce.exceptions;

import com.xdpsx.ecommerce.constants.messages.APIMessage;

public class ModifyExclusiveException extends APIException {
    public ModifyExclusiveException(String message, Object... args) {
        super(message, args);
    }

    public ModifyExclusiveException(APIMessage apiMessage, Object... args) {
        super(apiMessage.message(), args);
    }
}
