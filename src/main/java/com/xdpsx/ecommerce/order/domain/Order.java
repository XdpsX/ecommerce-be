package com.xdpsx.ecommerce.order.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.user.domain.User;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @OneToMany(
            mappedBy = "order",
            cascade = {CascadeType.PERSIST, CascadeType.REMOVE})
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private OrderStatus status;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String mobileNumber;

    @Column(length = 500)
    private String description;

    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key_hash", columnDefinition = "BINARY(32)")
    private byte[] idempotencyKeyHash;

    @Column(name = "checkout_request_hash", columnDefinition = "BINARY(32)")
    private byte[] checkoutRequestHash;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Embedded
    private ShippingAddressSnapshot shippingAddress;

    @Column(nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deliveredAt;

    @OneToOne(mappedBy = "order", cascade = CascadeType.PERSIST)
    private Payment payment;

    public void setIdempotencyKeyHash(byte[] value) {
        idempotencyKeyHash = value == null ? null : value.clone();
    }

    public void setCheckoutRequestHash(byte[] value) {
        checkoutRequestHash = value == null ? null : value.clone();
    }

    public void setShippingAddress(ShippingAddressSnapshot value) {
        value.validateComplete();
        shippingAddress = value;
        address = value.getAddressLine();
        mobileNumber = value.getPhoneNumber();
    }

    public void confirmPayment() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending Orders can be confirmed");
        }
        status = OrderStatus.CONFIRMED;
    }

    public void markPaymentExpired() {
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending Orders can expire");
        }
        status = OrderStatus.PAYMENT_EXPIRED;
    }

    public void advanceTo(OrderStatus nextStatus) {
        if (nextStatus == null
                || !((status == OrderStatus.CONFIRMED && nextStatus == OrderStatus.PROCESSING)
                        || (status == OrderStatus.PROCESSING && nextStatus == OrderStatus.SHIPPED)
                        || (status == OrderStatus.SHIPPED && nextStatus == OrderStatus.DELIVERED))) {
            throw new IllegalStateException("Invalid Order status transition");
        }
        status = nextStatus;
        if (nextStatus == OrderStatus.DELIVERED) deliveredAt = LocalDateTime.now();
    }
}
