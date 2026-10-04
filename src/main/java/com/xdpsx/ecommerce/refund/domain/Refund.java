package com.xdpsx.ecommerce.refund.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.payment.domain.Payment;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_refunds_payment_id", columnNames = "payment_id"),
            @UniqueConstraint(name = "uk_refunds_external_reference", columnNames = "external_reference")
        })
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private RefundStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false, length = 320)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_by", length = 320)
    private String processedBy;

    private LocalDateTime completedAt;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public void complete(String actor, LocalDateTime completedAt, String externalReference) {
        if (status == RefundStatus.SUCCEEDED) return;
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Only a pending Refund can succeed");
        }
        this.status = RefundStatus.SUCCEEDED;
        this.processedBy = actor;
        this.completedAt = completedAt;
        this.externalReference = externalReference;
        this.failureReason = null;
    }

    public void fail(String actor, LocalDateTime completedAt, String failureReason) {
        if (status == RefundStatus.FAILED && java.util.Objects.equals(this.failureReason, failureReason)) return;
        if (status != RefundStatus.PENDING) {
            throw new IllegalStateException("Only a pending Refund can fail");
        }
        this.status = RefundStatus.FAILED;
        this.processedBy = actor;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.externalReference = null;
    }

    public void retry() {
        if (status != RefundStatus.FAILED) {
            throw new IllegalStateException("Only a failed Refund can be retried");
        }
        status = RefundStatus.PENDING;
        failureReason = null;
        externalReference = null;
    }
}
