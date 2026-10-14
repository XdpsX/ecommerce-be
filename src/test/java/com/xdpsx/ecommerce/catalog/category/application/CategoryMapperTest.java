package com.xdpsx.ecommerce.catalog.category.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.media.application.MediaUrlResolver;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class CategoryMapperTest {
    private final MediaUrlResolver mediaUrlResolver = new MediaUrlResolver(
            (externalId, purpose) -> Map.of("categoryCard", "https://cdn.example.test/category-card.jpg"));

    @Test
    void storefrontCategory_shouldUseCategoryCardVariant() {
        StorefrontCategoryResponse response =
                CategoryMapper.INSTANCE.toStorefrontCategoryResponse(categoryWithImage(), mediaUrlResolver);

        assertThat(response.image()).isEqualTo("https://cdn.example.test/category-card.jpg");
    }

    @Test
    void storefrontTree_shouldUseCategoryCardVariant() {
        CategoryTreeResponse response =
                CategoryMapper.INSTANCE.toCategoryTreeResponse(categoryWithImage(), mediaUrlResolver);

        assertThat(response.getImage()).isEqualTo("https://cdn.example.test/category-card.jpg");
    }

    private static Category categoryWithImage() {
        Media image = Media.builder()
                .id("media-id")
                .externalId("external-category")
                .url("https://cdn.example.test/original.jpg")
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .build();
        return Category.builder()
                .id(1)
                .name("Category")
                .slug("category")
                .status(CategoryStatus.ACTIVE)
                .image(image)
                .build();
    }
}
