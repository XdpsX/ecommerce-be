package com.xdpsx.ecommerce.dtos.product;

import java.math.BigDecimal;

import com.xdpsx.ecommerce.dtos.brand.BrandNoCatsDTO;
import com.xdpsx.ecommerce.dtos.category.CategoryResponse;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class ProductResponse {
    private Long id;
    private String name;
    private String slug;
    private BigDecimal price;
    private BigDecimal discountedPrice;
    private double discountPercent;
    private boolean inStock;
    private boolean published;
    private String mainImage;
    private CategoryResponse category;
    private BrandNoCatsDTO brand;
}
