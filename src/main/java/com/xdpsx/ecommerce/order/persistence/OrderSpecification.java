package com.xdpsx.ecommerce.order.persistence;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;

public class OrderSpecification {
    public static Specification<Order> withStatusAndPaymentStatus(
            OrderStatus orderStatus, PaymentStatus paymentStatus) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (orderStatus != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), orderStatus));
            }

            if (paymentStatus != null) {
                Join<Order, Payment> paymentJoin = root.join("payment");
                predicate =
                        criteriaBuilder.and(predicate, criteriaBuilder.equal(paymentJoin.get("status"), paymentStatus));
            }

            return predicate;
        };
    }
}
