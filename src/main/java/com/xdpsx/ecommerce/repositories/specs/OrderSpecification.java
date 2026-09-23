package com.xdpsx.ecommerce.repositories.specs;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.entities.Order;
import com.xdpsx.ecommerce.entities.Payment;
import com.xdpsx.ecommerce.entities.enums.OrderStatus;
import com.xdpsx.ecommerce.entities.enums.PaymentStatus;

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
