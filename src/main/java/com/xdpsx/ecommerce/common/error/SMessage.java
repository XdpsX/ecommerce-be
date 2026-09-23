package com.xdpsx.ecommerce.common.error;

/***
 * Successful Messages (Green color)
 */
public enum SMessage implements APIMessage {
    SUCCESS("S_MESSAGE_0001"),
    CREATE_SUCCESSFULLY("S_MESSAGE_0002"),
    UPDATE_SUCCESSFULLY("S_MESSAGE_0003"),
    DELETE_SUCCESSFULLY("S_MESSAGE_0004");

    private final String message;

    SMessage(String message) {
        this.message = message;
    }

    public String message() {
        return this.message;
    }
}
