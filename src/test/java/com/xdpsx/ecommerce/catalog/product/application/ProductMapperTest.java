package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.product.api.dto.AdminProductSummaryResponse;
import com.xdpsx.ecommerce.catalog.product.domain.Product;

class ProductMapperTest {
    private final ProductMapper productMapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void toAdminSummary_ShouldExposeLifecycleStateThatExplainsStorefrontVisibility() {
        Category inactiveParent = Category.builder()
                .id(1)
                .name("Parent")
                .slug("parent")
                .status(CategoryStatus.INACTIVE)
                .build();
        Category activeCategory = Category.builder()
                .id(2)
                .name("Category")
                .slug("category")
                .status(CategoryStatus.ACTIVE)
                .parent(inactiveParent)
                .build();
        Brand inactiveBrand =
                Brand.builder().id(3).name("Brand").status(BrandStatus.INACTIVE).build();
        Product product = Product.builder()
                .id(4L)
                .name("Product")
                .slug("product")
                .price(BigDecimal.TEN)
                .published(true)
                .category(activeCategory)
                .brand(inactiveBrand)
                .build();

        AdminProductSummaryResponse response = productMapper.toAdminSummary(product, true);

        assertThat(response.inStock()).isTrue();
        assertThat(response.published()).isTrue();
        assertThat(response.brand().status()).isEqualTo(BrandStatus.INACTIVE);
        assertThat(response.category().status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(response.category().effectivelyActive()).isFalse();
    }
}
