package com.xdpsx.ecommerce.catalog.product.domain;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private ProductVariantStatus status;

    @Column(name = "combination_key", length = 700, nullable = false)
    private String combinationKey;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.optionId ASC")
    @Builder.Default
    private List<ProductVariantSelection> selections = new ArrayList<>();
}
