package com.xdpsx.ecommerce.media.application;

/** Transient provider failure; callers may retry without changing Media state. */
public class MediaAssetProviderException extends RuntimeException {
    private final int requestsAttempted;

    public MediaAssetProviderException(String message, Throwable cause) {
        this(message, cause, 1);
    }

    public MediaAssetProviderException(String message, Throwable cause, int requestsAttempted) {
        super(message, cause);
        this.requestsAttempted = Math.max(1, requestsAttempted);
    }

    public int requestsAttempted() {
        return requestsAttempted;
    }
}
