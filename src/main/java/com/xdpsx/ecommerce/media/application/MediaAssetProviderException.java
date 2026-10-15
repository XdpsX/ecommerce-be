package com.xdpsx.ecommerce.media.application;

/** Transient provider failure; callers may retry without changing Media state. */
public class MediaAssetProviderException extends RuntimeException {
    public MediaAssetProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
