package com.xdpsx.ecommerce.cart.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class GuestCartCredentialService {
    private static final int SECRET_BYTES = 32;
    private static final int MAX_CREDENTIAL_LENGTH = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedSecret issueSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return new IssuedSecret(secret, hash(secret));
    }

    public String format(Long cartId, byte[] secret) {
        if (cartId == null || cartId <= 0 || secret == null || secret.length != SECRET_BYTES) {
            throw new IllegalArgumentException("Invalid guest Cart credential input");
        }
        return cartId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public Optional<ParsedCredential> parse(String credential) {
        if (credential == null || credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return Optional.empty();
        }
        int separator = credential.indexOf('.');
        if (separator <= 0 || separator != credential.lastIndexOf('.') || separator == credential.length() - 1) {
            return Optional.empty();
        }
        try {
            long cartId = Long.parseLong(credential.substring(0, separator));
            if (cartId <= 0) return Optional.empty();
            String encodedSecret = credential.substring(separator + 1);
            byte[] secret = Base64.getUrlDecoder().decode(encodedSecret);
            String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
            if (secret.length != SECRET_BYTES || !canonical.equals(encodedSecret)) return Optional.empty();
            return Optional.of(new ParsedCredential(cartId, secret));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean matches(byte[] expectedHash, byte[] secret) {
        return expectedHash != null && MessageDigest.isEqual(expectedHash, hash(secret));
    }

    private byte[] hash(byte[] secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for guest Cart credentials", exception);
        }
    }

    public record IssuedSecret(byte[] secret, byte[] secretHash) {}

    public record ParsedCredential(Long cartId, byte[] secret) {}
}
