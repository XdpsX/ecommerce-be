package com.xdpsx.ecommerce.order.domain;

import java.math.BigDecimal;
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

    @Column(nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deliveredAt;

    @OneToOne(mappedBy = "order", cascade = CascadeType.PERSIST)
    private Payment payment;
}
