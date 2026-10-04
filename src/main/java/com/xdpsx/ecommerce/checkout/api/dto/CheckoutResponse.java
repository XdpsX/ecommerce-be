package com.xdpsx.ecommerce.checkout.api.dto;

import com.xdpsx.ecommerce.order.api.dto.OrderDTO;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckoutResponse {
    private OrderDTO order;
    private InitPaymentResponse payment;
    private boolean replayed;
}
