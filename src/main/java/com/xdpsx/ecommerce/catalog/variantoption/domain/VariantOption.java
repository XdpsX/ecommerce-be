package com.xdpsx.ecommerce.catalog.variantoption.domain;

import java.util.ArrayList;
import java.util.List;

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
        name = "variant_options",
        indexes = {@Index(name = "ix_variant_option_status_order", columnList = "status, display_order, id")})
public class VariantOption extends AuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable technical identity. It is canonicalized once and never changed by the admin API. */
    @Column(length = 64, nullable = false, unique = true)
    private String code;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private VariantOptionStatus status;

    @OneToMany(mappedBy = "option", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    @Builder.Default
    private List<VariantOptionValue> values = new ArrayList<>();
}
