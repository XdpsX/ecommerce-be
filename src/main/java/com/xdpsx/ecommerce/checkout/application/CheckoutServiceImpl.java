package com.xdpsx.ecommerce.checkout.application;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutResponse;
import com.xdpsx.ecommerce.order.api.dto.OrderDTO;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentAttemptService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final CheckoutTransactionService transactionService;
    private final PaymentAttemptService paymentAttemptService;

    @Override
    public CheckoutResponse checkout(
            String userEmail, CheckoutRequest request, String idempotencyKey, String ipAddress) {
        CheckoutTransactionResult result = transactionService.execute(userEmail, request, idempotencyKey);
        Order order = result.order();
        InitPaymentResponse payment = null;
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            payment = paymentAttemptService.initialize(userEmail, order.getId(), ipAddress);
        }
        return CheckoutResponse.builder()
                .order(toDto(order))
                .payment(payment)
                .replayed(result.replayed())
                .build();
    }

    private static OrderDTO toDto(Order order) {
        var shipping = order.getShippingAddress();
        return OrderDTO.builder()
                .id(order.getId())
                .trackingNumber(order.getTrackingNumber())
                .status(order.getStatus().name())
                .total(order.getTotalAmount())
                .address(shipping == null ? order.getAddress() : shipping.getAddressLine())
                .mobileNumber(shipping == null ? order.getMobileNumber() : shipping.getPhoneNumber())
                .recipientName(shipping == null ? null : shipping.getRecipientName())
                .phoneNumber(shipping == null ? null : shipping.getPhoneNumber())
                .addressLine(shipping == null ? order.getAddress() : shipping.getAddressLine())
                .wardCommune(shipping == null ? null : shipping.getWardCommune())
                .district(shipping == null ? null : shipping.getDistrict())
                .provinceCity(shipping == null ? null : shipping.getProvinceCity())
                .postalCode(shipping == null ? null : shipping.getPostalCode())
                .currency(order.getCurrency())
                .paymentStatus(
                        order.getPayment() == null
                                ? null
                                : order.getPayment().getStatus().name())
                .createdAt(order.getCreatedAt() == null ? null : order.getCreatedAt())
                .deliveredAt(order.getDeliveredAt())
                .build();
    }
}
