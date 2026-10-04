package com.xdpsx.ecommerce.catalog.product.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
@Getter
@Setter
public class ProductVariantSelectionId implements Serializable {
    @Column(name = "variant_id")
    private Long variantId;

    @Column(name = "option_id")
    private Long optionId;
}
