package com.xdpsx.ecommerce.catalog.product.domain;

import jakarta.persistence.*;

import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "product_variant_selections",
        indexes = {@Index(name = "ix_variant_selection_option_value", columnList = "option_value_id, variant_id")})
public class ProductVariantSelection {
    @EmbeddedId
    private ProductVariantSelectionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("variantId")
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "option_value_id", nullable = false)
    private Long optionValueId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
        @JoinColumn(name = "option_id", referencedColumnName = "option_id", insertable = false, updatable = false),
        @JoinColumn(name = "option_value_id", referencedColumnName = "id", insertable = false, updatable = false)
    })
    private VariantOptionValue optionValue;
}
