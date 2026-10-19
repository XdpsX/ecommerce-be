package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.product.api.dto.AdminProductSummaryResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductCreateRequest;
import com.xdpsx.ecommerce.catalog.product.api.dto.StorefrontProductDetailResponse;
import com.xdpsx.ecommerce.catalog.product.api.dto.StorefrontProductSummaryResponse;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductImage;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.media.application.delivery.MediaUrlResolver;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class ProductMapperTest {
    private final MediaUrlResolver mediaUrlResolver = new MediaUrlResolver((externalId, purpose) -> Map.of(
            "productCard", "https://cdn.example.test/product-card.jpg",
            "productDetail", "https://cdn.example.test/product-detail.jpg"));
    private final ProductMapper productMapper = new ProductMapper(new StorePricingProperties(), mediaUrlResolver);

    @Test
    void fromCreateRequestToEntity_ShouldMapEditableFieldsAndLeaveRelationsForService() {
        ProductCreateRequest request = ProductCreateRequest.builder()
                .name("Shirt")
                .slug("shirt")
                .description("Cotton shirt")
                .categoryId(2)
                .brandId(3)
                .imageIds(List.of("image-id"))
                .build();

        Product product = productMapper.fromCreateRequestToEntity(request);

        assertThat(product.getName()).isEqualTo("Shirt");
        assertThat(product.getSlug()).isEqualTo("shirt");
        assertThat(product.getDescription()).isEqualTo("Cotton shirt");
        assertThat(product.isPublished()).isFalse();
        assertThat(product.getId()).isNull();
        assertThat(product.getCategory()).isNull();
        assertThat(product.getBrand()).isNull();
        assertThat(product.getImages()).isEmpty();
        assertThat(product.getVariants()).isEmpty();
    }

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

    @Test
    void storefrontSummary_shouldUseProductCardVariant() {
        StorefrontProductSummaryResponse response = productMapper.toStorefrontSummary(
                productWithImage(), false, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);

        assertThat(response.mainImage()).isEqualTo("https://cdn.example.test/product-card.jpg");
    }

    @Test
    void storefrontDetail_shouldUseProductDetailVariantForMainImageAndGallery() {
        StorefrontProductDetailResponse response = productMapper.toStorefrontDetail(
                productWithImage(), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, List.of(), List.of(), false);

        assertThat(response.mainImage()).isEqualTo("https://cdn.example.test/product-detail.jpg");
        assertThat(response.images()).extracting("url").containsExactly("https://cdn.example.test/product-detail.jpg");
    }

    @Test
    void adminDetail_shouldKeepOriginalImageUrl() {
        var response = productMapper.toAdminDetail(productWithImage(), List.of(), List.of());

        assertThat(response.mainImage()).isEqualTo("https://cdn.example.test/original.jpg");
        assertThat(response.images()).extracting("url").containsExactly("https://cdn.example.test/original.jpg");
    }

    private static Product productWithImage() {
        Media media = Media.builder()
                .id("media-id")
                .externalId("external-product")
                .url("https://cdn.example.test/original.jpg")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.ACTIVE)
                .build();
        ProductImage image = ProductImage.builder().media(media).displayOrder(0).build();
        return Product.builder()
                .id(9L)
                .name("Product")
                .slug("product")
                .images(List.of(image))
                .build();
    }
}
