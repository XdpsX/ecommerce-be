package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.common.PageResponse;
import com.xdpsx.ecommerce.dtos.order.*;
import com.xdpsx.ecommerce.entities.enums.OrderStatus;
import com.xdpsx.ecommerce.entities.enums.PaymentStatus;

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
