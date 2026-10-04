package com.xdpsx.ecommerce.payment.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentAttemptService {
    private final PaymentAttemptPreparationService preparationService;
    private final PaymentService paymentService;

    public InitPaymentResponse initialize(String userEmail, Long orderId, String ipAddress) {
        PreparedPaymentAttempt prepared = preparationService.prepare(userEmail, orderId);
        try {
            InitPaymentResponse response = paymentService.init(InitPaymentRequest.builder()
                    .requestId(prepared.providerReference())
                    .userId(prepared.userId())
                    .txnRef(prepared.providerReference())
                    .amount(prepared.expectedAmount())
                    .currency(prepared.currency())
                    .expiresAt(prepared.expiresAt())
                    .ipAddress(ipAddress)
                    .build());
            response.setAttemptReference(prepared.providerReference());
            response.setExpiresAt(prepared.expiresAt());
            return response;
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                    ErrorCode.PAYMENT_INITIALIZATION_FAILED, Map.of("orderId", orderId), exception);
        }
    }
}
