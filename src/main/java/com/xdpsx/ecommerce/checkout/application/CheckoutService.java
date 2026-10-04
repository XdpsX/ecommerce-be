package com.xdpsx.ecommerce.checkout.application;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutResponse;

public interface CheckoutService {
    CheckoutResponse checkout(String userEmail, CheckoutRequest request, String idempotencyKey, String ipAddress);
}
