package com.xdpsx.ecommerce.order.application;

import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.order.api.dto.*;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

public interface OrderService {
    OrderResponse placeOrder(String userEmail, OrderRequest orderRequest);

    void payment(String userEmail, long orderId);

    PageResponse<OrderDTO> getMyOrders(String userEmail, int pageNum, int pageSize);

    OrderDetailsDTO getOrderByTrackingNumber(String name, String trackingNumber);

    OrderDetailsDTO getOrderById(Long orderId);

    PageResponse<OrderDTO> getAllOrders(
            int pageNum, int pageSize, OrderStatus orderStatus, PaymentStatus paymentStatus);

    OrderDTO updateOrderStatus(Long id, OrderStatusUpdate request);
}
