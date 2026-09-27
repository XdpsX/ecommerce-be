package com.xdpsx.ecommerce.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xdpsx.ecommerce.order.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    boolean existsByProductId(Long productId);
}
