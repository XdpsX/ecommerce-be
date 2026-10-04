package com.xdpsx.ecommerce.inventory.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "inventory_adjustments")
@EntityListeners(AuditingEntityListener.class)
public class InventoryAdjustment {
    public static final int MAX_PERFORMED_BY_LENGTH = 320;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 320)
    private String performedBy;

    @Column(name = "on_hand_after", nullable = false)
    private long onHandAfter;

    @Column(name = "reserved_after", nullable = false)
    private long reservedAfter;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public InventoryAdjustment(
            Long variantId,
            long quantityDelta,
            String reason,
            String performedBy,
            long onHandAfter,
            long reservedAfter) {
        this.variantId = variantId;
        this.quantityDelta = quantityDelta;
        this.reason = reason;
        this.performedBy = normalizePerformedBy(performedBy);
        this.onHandAfter = onHandAfter;
        this.reservedAfter = reservedAfter;
    }

    private static String normalizePerformedBy(String performedBy) {
        if (performedBy == null) {
            throw new IllegalArgumentException("performedBy is required");
        }
        String normalized = performedBy.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_PERFORMED_BY_LENGTH) {
            throw new IllegalArgumentException("performedBy must contain 1 to 320 characters after trimming");
        }
        return normalized;
    }
}
