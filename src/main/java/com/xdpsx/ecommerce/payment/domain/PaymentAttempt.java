package com.xdpsx.ecommerce.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_payment_attempts_provider_reference", columnNames = "provider_reference"),
            @UniqueConstraint(
                    name = "uk_payment_attempts_provider_transaction_id",
                    columnNames = "provider_transaction_id")
        })
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "provider_reference", length = 100, nullable = false)
    private String providerReference;

    @Column(name = "provider_transaction_id", length = 32)
    private String providerTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private PaymentAttemptStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal expectedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "response_code", length = 16)
    private String responseCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant completedAt;

    public boolean isActiveAt(Instant now) {
        return status == PaymentAttemptStatus.PENDING && expiresAt.isAfter(now);
    }

    public void markExpired(Instant completedAt) {
        if (status == PaymentAttemptStatus.EXPIRED) return;
        if (status != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException("Only a pending PaymentAttempt can expire");
        }
        status = PaymentAttemptStatus.EXPIRED;
        this.completedAt = completedAt;
    }

    public void markFailed(String responseCode, Instant completedAt) {
        if (status != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException("Only a pending PaymentAttempt can fail");
        }
        status = PaymentAttemptStatus.FAILED;
        this.responseCode = responseCode;
        this.completedAt = completedAt;
    }

    public void markCancelled(Instant completedAt) {
        if (status == PaymentAttemptStatus.CANCELLED) return;
        if (status != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException("Only a pending PaymentAttempt can be cancelled");
        }
        status = PaymentAttemptStatus.CANCELLED;
        this.completedAt = completedAt;
    }

    public void markSucceeded(String providerTransactionId, String responseCode, Instant completedAt) {
        if (status == PaymentAttemptStatus.SUCCEEDED) return;
        if (status != PaymentAttemptStatus.PENDING
                && status != PaymentAttemptStatus.FAILED
                && status != PaymentAttemptStatus.EXPIRED
                && status != PaymentAttemptStatus.CANCELLED) {
            throw new IllegalStateException("Only an unresolved PaymentAttempt can succeed");
        }
        if (providerTransactionId == null || providerTransactionId.isBlank() || providerTransactionId.length() > 32) {
            throw new IllegalArgumentException("Provider transaction ID is invalid");
        }
        status = PaymentAttemptStatus.SUCCEEDED;
        this.providerTransactionId = providerTransactionId;
        this.responseCode = responseCode;
        this.completedAt = completedAt;
    }
}
