package com.xdpsx.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.order.api.dto.*;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

public interface OrderService {
    PaymentCallbackResult processPaymentCallback(long orderId, BigDecimal amount, boolean successful);

    boolean expirePendingOrder(long orderId, Instant cutoff);

    PageResponse<OrderDTO> getMyOrders(String userEmail, int pageNum, int pageSize);

    OrderDetailsDTO getOrderByTrackingNumber(String name, String trackingNumber);

    OrderDetailsDTO getOrderById(Long orderId);

    PageResponse<OrderDTO> getAllOrders(
            int pageNum, int pageSize, OrderStatus orderStatus, PaymentStatus paymentStatus);

    OrderDTO updateOrderStatus(Long id, OrderStatusUpdate request);
}
