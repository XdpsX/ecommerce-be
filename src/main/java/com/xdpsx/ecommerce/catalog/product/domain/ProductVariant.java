package com.xdpsx.ecommerce.catalog.product.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.common.persistence.AuditEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "product_variants",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"),
            @UniqueConstraint(name = "uk_product_variant_barcode", columnNames = "barcode"),
            @UniqueConstraint(
                    name = "uk_product_variant_combination",
                    columnNames = {"product_id", "combination_key"})
        },
        indexes = {@Index(name = "ix_product_variant_product_status", columnList = "product_id, status")})
@EntityListeners(AuditingEntityListener.class)
public class ProductVariant extends AuditEntity {
    public static final BigDecimal MAX_BASE_PRICE = new BigDecimal("1000000000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 128, nullable = false)
    private String sku;

    @Column(length = 128)
    private String barcode;

    @Builder.Default
    @Column(name = "base_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal basePrice = BigDecimal.ZERO.setScale(2);

    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_starts_at")
    private Instant saleStartsAt;

    @Column(name = "sale_ends_at")
    private Instant saleEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private ProductVariantStatus status;

    @Column(name = "combination_key", length = 700, nullable = false)
    private String combinationKey;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.optionId ASC")
    @Builder.Default
    private List<ProductVariantSelection> selections = new ArrayList<>();

    public void changeBasePrice(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("basePrice must not be null");
        if (value.signum() < 0 || value.compareTo(MAX_BASE_PRICE) > 0) {
            throw new IllegalArgumentException("basePrice must be between 0.00 and 1000000000.00");
        }
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (salePrice != null && normalized.compareTo(salePrice) <= 0) {
                throw new IllegalArgumentException("basePrice must be greater than salePrice");
            }
            this.basePrice = normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("basePrice must have at most 2 fractional digits", exception);
        }
    }

    public void replaceSaleSchedule(BigDecimal value, Instant startsAt, Instant endsAt, Instant now) {
        if (value == null || startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("sale schedule must be complete");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("salePrice must have at most 2 fractional digits", exception);
        }
        if (normalized.signum() < 0 || normalized.compareTo(basePrice) >= 0) {
            throw new IllegalArgumentException("salePrice must be between 0.00 inclusive and basePrice exclusive");
        }
        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("saleStartsAt must be before saleEndsAt");
        }
        if (now == null || !endsAt.isAfter(now)) {
            throw new IllegalArgumentException("saleEndsAt must be after the current instant");
        }
        this.salePrice = normalized;
        this.saleStartsAt = startsAt;
        this.saleEndsAt = endsAt;
    }

    public void clearSaleSchedule() {
        this.salePrice = null;
        this.saleStartsAt = null;
        this.saleEndsAt = null;
    }

    public ResolvedVariantPrice resolvePriceAt(Instant now) {
        BigDecimal normalizedBase = basePrice == null ? BigDecimal.ZERO.setScale(2) : basePrice.setScale(2);
        boolean active = salePrice != null
                && saleStartsAt != null
                && saleEndsAt != null
                && now != null
                && !now.isBefore(saleStartsAt)
                && now.isBefore(saleEndsAt);
        BigDecimal finalPrice = active ? salePrice : normalizedBase;
        return new ResolvedVariantPrice(
                normalizedBase, normalizedBase.subtract(finalPrice).setScale(2), finalPrice.setScale(2));
    }

    @PrePersist
    @PreUpdate
    private void validateBasePrice() {
        changeBasePrice(basePrice);
        if (salePrice == null && saleStartsAt == null && saleEndsAt == null) return;
        if (salePrice == null || saleStartsAt == null || saleEndsAt == null) {
            throw new IllegalArgumentException("sale schedule must be complete");
        }
        try {
            salePrice = salePrice.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("salePrice must have at most 2 fractional digits", exception);
        }
        if (salePrice.signum() < 0 || salePrice.compareTo(basePrice) >= 0) {
            throw new IllegalArgumentException("salePrice must be between 0.00 inclusive and basePrice exclusive");
        }
        if (!saleStartsAt.isBefore(saleEndsAt)) {
            throw new IllegalArgumentException("saleStartsAt must be before saleEndsAt");
        }
    }
}
