package com.xdpsx.ecommerce.cart.application;

import java.time.Instant;

import com.xdpsx.ecommerce.cart.api.dto.CartResponse;

public record CartMutationResult(
        CartResponse response, String guestCredential, Instant guestExpiresAt, boolean expireGuestCookie) {}
