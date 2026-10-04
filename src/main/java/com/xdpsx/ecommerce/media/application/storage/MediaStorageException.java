package com.xdpsx.ecommerce.media.application.storage;

/**
 * Signals that the storage provider could not complete an upload or delete operation.
 *
 * <p>The message must stay provider-neutral and must never be exposed to API clients.
 */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message) {
        super(message);
    }

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
