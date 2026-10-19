package com.xdpsx.ecommerce.media.application.cleanup;

public class MediaCleanupProviderException extends RuntimeException {
    private final boolean rateLimited;

    public MediaCleanupProviderException(String message, Throwable cause) {
        super(message, cause);
        this.rateLimited = isRateLimit(message, cause);
    }

    public MediaCleanupProviderException(String message) {
        super(message);
        this.rateLimited = isRateLimit(message, null);
    }

    public boolean isRateLimited() {
        return rateLimited;
    }

    private static boolean isRateLimit(String message, Throwable cause) {
        String details = (message == null ? "" : message) + " " + (cause == null ? "" : cause.getMessage());
        return details.contains("429")
                || details.toLowerCase(java.util.Locale.ROOT).contains("rate limit");
    }
}
