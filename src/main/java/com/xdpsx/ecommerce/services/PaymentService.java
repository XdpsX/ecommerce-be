package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.payment.InitPaymentRequest;
import com.xdpsx.ecommerce.dtos.payment.InitPaymentResponse;

public interface PaymentService {
    InitPaymentResponse init(InitPaymentRequest request);
}
