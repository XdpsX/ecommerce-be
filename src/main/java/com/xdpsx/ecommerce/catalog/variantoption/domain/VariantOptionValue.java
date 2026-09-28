package com.xdpsx.ecommerce.catalog.variantoption.domain;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.common.persistence.AuditEntity;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "variant_option_values",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_variant_option_value_code",
                    columnNames = {"option_id", "code"}),
            @UniqueConstraint(
                    name = "uk_variant_option_value_option_id",
                    columnNames = {"option_id", "id"}),
            @UniqueConstraint(
                    name = "uk_variant_option_value_order",
                    columnNames = {"option_id", "display_order"})
        },
        indexes = {
            @Index(name = "ix_variant_option_value_status_order", columnList = "option_id, status, display_order, id")
        })
public class VariantOptionValue extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    private VariantOption option;

    /** Stable technical identity within one option. */
    @Column(length = 64, nullable = false)
    private String code;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private VariantOptionStatus status;
}
