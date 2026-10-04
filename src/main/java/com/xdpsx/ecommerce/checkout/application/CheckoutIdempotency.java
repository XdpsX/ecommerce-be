package com.xdpsx.ecommerce.checkout.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

final class CheckoutIdempotency {
    static final int MAX_KEY_LENGTH = 128;

    private CheckoutIdempotency() {}

    static String normalizeKey(String rawKey) {
        String key = rawKey == null ? null : rawKey.trim();
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, java.util.Map.of("field", "Idempotency-Key"));
        }
        return key;
    }

    static byte[] keyHash(String key) {
        return sha256(key);
    }

    static byte[] requestHash(CheckoutRequest request) {
        String description =
                request.getDescription() == null ? "" : request.getDescription().trim();
        return sha256(request.getAddressId() + "\n" + description);
    }

    static boolean same(byte[] left, byte[] right) {
        return left != null && right != null && MessageDigest.isEqual(left, right);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
