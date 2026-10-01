package com.xdpsx.ecommerce.auth.application;

import java.time.Instant;

public record RefreshRotationOutcome(
        boolean successful, String accessToken, String refreshCredential, Instant refreshExpiresAt) {
    public static RefreshRotationOutcome invalid() {
        return new RefreshRotationOutcome(false, null, null, null);
    }

    public AuthenticatedSession toAuthenticatedSession() {
        return new AuthenticatedSession(accessToken, refreshCredential, refreshExpiresAt);
    }
}
