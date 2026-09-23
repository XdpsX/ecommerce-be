package com.xdpsx.ecommerce.payment.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitPaymentResponse {
    private String vnpUrl;
}
