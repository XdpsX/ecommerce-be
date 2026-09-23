package com.xdpsx.ecommerce.payment.application;

import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.domain.Payment;

public interface PaymentService {
    InitPaymentResponse init(InitPaymentRequest request);
}
