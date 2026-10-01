package com.xdpsx.ecommerce.auth.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class RefreshCredentialService {
    private static final int SECRET_BYTES = 32;
    private static final int MAX_CREDENTIAL_LENGTH = 128;

    private final SecureRandom secureRandom = new SecureRandom();

    public IssuedCredential issue(String sessionId) {
        byte[] secret = new byte[SECRET_BYTES];
        secureRandom.nextBytes(secret);
        String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new IssuedCredential(sessionId + "." + encodedSecret, hash(secret));
    }

    public String newSessionId() {
        return UUID.randomUUID().toString();
    }

    public Optional<ParsedCredential> parse(String credential) {
        if (credential == null || credential.isBlank() || credential.length() > MAX_CREDENTIAL_LENGTH) {
            return Optional.empty();
        }
        int separator = credential.indexOf('.');
        if (separator <= 0 || separator != credential.lastIndexOf('.') || separator == credential.length() - 1) {
            return Optional.empty();
        }
        String sessionId = credential.substring(0, separator);
        String encodedSecret = credential.substring(separator + 1);
        try {
            UUID.fromString(sessionId);
            byte[] secret = Base64.getUrlDecoder().decode(encodedSecret);
            if (secret.length != SECRET_BYTES
                    || !Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(secret)
                            .equals(encodedSecret)) {
                return Optional.empty();
            }
            return Optional.of(new ParsedCredential(sessionId, secret));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public byte[] hash(byte[] secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for refresh credentials", exception);
        }
    }

    public boolean matches(byte[] expectedHash, byte[] secret) {
        return expectedHash != null && MessageDigest.isEqual(expectedHash, hash(secret));
    }

    public record IssuedCredential(String rawCredential, byte[] secretHash) {}

    public record ParsedCredential(String sessionId, byte[] secret) {}
}
