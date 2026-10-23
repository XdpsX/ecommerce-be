package com.xdpsx.ecommerce.catalog.category.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.media.application.delivery.MediaUrlResolver;
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

    @Test
    void adminCategory_shouldExposeImageAndPersistedAuditFields() {
        Category category = categoryWithImage();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 22, 10, 0);
        category.setCreatedBy(17L);
        category.setUpdatedBy(23L);
        category.setCreatedAt(createdAt);

        AdminCategoryResponse response = CategoryMapper.INSTANCE.toAdminCategoryResponse(category);

        assertThat(response.imageId()).isEqualTo("media-id");
        assertThat(response.image()).isEqualTo("https://cdn.example.test/original.jpg");
        assertThat(response.createdBy()).isEqualTo(17L);
        assertThat(response.updatedBy()).isEqualTo(23L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void adminCategoryWithoutImage_shouldExposeNullImageId() {
        AdminCategoryResponse response = CategoryMapper.INSTANCE.toAdminCategoryResponse(Category.builder()
                .id(2)
                .name("Storage")
                .slug("storage")
                .status(CategoryStatus.INACTIVE)
                .build());

        assertThat(response.imageId()).isNull();
        assertThat(response.image()).isNull();
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
