package com.xdpsx.ecommerce.payment.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.order.domain.Order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime paymentDate;

    public void markPending() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending Payment can remain pending");
        }
        status = PaymentStatus.PENDING;
    }

    public void markPaid(PaymentMethod method, LocalDateTime paidAt) {
        if (status == PaymentStatus.PAID) return;
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending Payment can become paid");
        }
        status = PaymentStatus.PAID;
        paymentMethod = method;
        paymentDate = paidAt;
    }

    public void markExpired() {
        if (status == PaymentStatus.EXPIRED) return;
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only a pending Payment can expire");
        }
        status = PaymentStatus.EXPIRED;
    }
}
