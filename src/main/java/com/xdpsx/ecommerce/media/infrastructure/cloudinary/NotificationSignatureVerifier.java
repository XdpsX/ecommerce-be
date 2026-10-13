package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

public interface NotificationSignatureVerifier {
    boolean isValid(String signature, String timestamp, String rawBody);
}
