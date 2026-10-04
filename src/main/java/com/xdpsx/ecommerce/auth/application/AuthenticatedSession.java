package com.xdpsx.ecommerce.auth.application;

import java.time.Instant;

public record AuthenticatedSession(String accessToken, String refreshCredential, Instant refreshExpiresAt) {}
