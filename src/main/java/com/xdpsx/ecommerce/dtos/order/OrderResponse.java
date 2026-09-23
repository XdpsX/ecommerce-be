package com.xdpsx.ecommerce.dtos.order;

import com.xdpsx.ecommerce.dtos.payment.InitPaymentResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {
    private OrderDTO order;
    private InitPaymentResponse payment;
}
