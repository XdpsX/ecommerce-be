package com.xdpsx.ecommerce.dtos.payment;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitPaymentRequest {
    private String requestId;

    private String ipAddress;

    private Long userId;

    private String txnRef;

    private BigDecimal amount;
}
