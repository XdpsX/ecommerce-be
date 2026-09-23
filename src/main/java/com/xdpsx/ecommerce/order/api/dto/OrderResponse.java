package com.xdpsx.ecommerce.order.api.dto;

import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {
    private OrderDTO order;
    private InitPaymentResponse payment;
}
