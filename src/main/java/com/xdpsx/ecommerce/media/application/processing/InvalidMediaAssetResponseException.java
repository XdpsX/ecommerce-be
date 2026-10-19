package com.xdpsx.ecommerce.media.application.processing;

/** Provider returned a successful response without usable authoritative asset metadata. */
public class InvalidMediaAssetResponseException extends RuntimeException {
    public InvalidMediaAssetResponseException(String message) {
        super(message);
    }

    public InvalidMediaAssetResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
