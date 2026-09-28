package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductCreateRequest;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductParams;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductUpdateRequest;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductSpecification;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.shared.application.PageMapper;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;
import com.xdpsx.ecommerce.order.persistence.OrderItemRepository;

/**
 * Guards the category assignment rule on Product: create and category
 * reassignment only accept an effectively
 * active category (the node and every ancestor stored ACTIVE), and a hidden or
 * missing category keeps the
 * {@code RESOURCE_NOT_FOUND} contract.
 *
 * <p>
 * Only the repositories/mapper are mocked; the visibility decision comes from
 * the real
 * {@link Category#isEffectivelyActive()} chain walk.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private PageMapper pageMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductSpecification productSpecification;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private static Category activeCategory(Integer id) {
        return Category.builder()
                .id(id)
                .name("Category " + id)
                .slug("category-" + id)
                .status(CategoryStatus.ACTIVE)
                .displayOrder(0)
                .build();
    }

    private static ProductCreateRequest createRequest(Integer categoryId) {
        return ProductCreateRequest.builder()
                .name("Keyboard")
                .slug("keyboard")
                .categoryId(categoryId)
                .brandId(5)
                .build();
    }

    @Test
    void createProduct_ShouldAssignEffectivelyActiveCategory() {
        // Arrange
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        when(productRepository.existsBySlug("keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findById(5)).thenReturn(Optional.of(brand));
        when(productMapper.fromCreateRequestToEntity(any())).thenReturn(new Product());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        productService.createProduct(createRequest(7));

        // Assert
        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertSame(category, captor.getValue().getCategory());
    }

    @Test
    void createProduct_ShouldRejectActiveCategoryUnderInactiveAncestor() {
        // Arrange: stored ACTIVE, but hidden from the storefront by an inactive parent.
        Category inactiveParent = Category.builder()
                .id(1)
                .name("Retired")
                .status(CategoryStatus.INACTIVE)
                .build();
        Category hidden = Category.builder()
                .id(7)
                .name("Hidden")
                .status(CategoryStatus.ACTIVE)
                .parent(inactiveParent)
                .build();
        when(productRepository.existsBySlug("keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(hidden));

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.createProduct(createRequest(7)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        assertEquals("category", exception.getParameters().get("resourceType"));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_ShouldRejectCategoryReassignment_WhenNewCategoryIsNotEffectivelyActive() {
        // Arrange: the product keeps its current category; the requested one is hidden.
        Category current = activeCategory(3);
        Category hidden = Category.builder()
                .id(7)
                .name("Hidden")
                .status(CategoryStatus.INACTIVE)
                .build();
        Product product = new Product();
        product.setName("Keyboard");
        product.setSlug("keyboard");
        product.setCategory(current);
        product.setImages(new java.util.ArrayList<>());

        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Keyboard");
        request.setSlug("keyboard");
        request.setPrice(BigDecimal.TEN);
        request.setCategoryId(7);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(hidden));

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.updateProduct(1L, request));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        verify(productRepository, never()).save(any(Product.class));
        // The stored assignment is untouched by a rejected reassignment.
        assertSame(current, product.getCategory());
    }

    @Test
    void createProduct_ShouldActivateOrderedTemporaryMedia() {
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Media first = media("m-1", MediaStatus.TEMPORARY);
        Media second = media("m-2", MediaStatus.TEMPORARY);
        when(productRepository.existsBySlug("keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findById(5)).thenReturn(Optional.of(brand));
        when(mediaRepository.findAllByIdIn(java.util.List.of("m-1", "m-2")))
                .thenReturn(java.util.List.of(first, second));
        when(productMapper.fromCreateRequestToEntity(any())).thenReturn(new Product());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductCreateRequest request = createRequest(7);
        request.setImageIds(java.util.List.of("m-1", "m-2"));

        productService.createProduct(request);

        assertEquals(MediaStatus.ACTIVE, first.getStatus());
        assertEquals(MediaStatus.ACTIVE, second.getStatus());
        org.mockito.ArgumentCaptor<Product> captor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals(
                java.util.List.of(0, 1),
                captor.getValue().getImages().stream()
                        .map(com.xdpsx.ecommerce.catalog.product.domain.ProductImage::getDisplayOrder)
                        .toList());
    }

    @Test
    void updateProduct_ShouldReorderAttachAndMarkRemovedMediaPendingDelete() {
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Media removed = media("m-old", MediaStatus.ACTIVE);
        Media retained = media("m-retained", MediaStatus.ACTIVE);
        Media added = media("m-new", MediaStatus.TEMPORARY);
        Product product = productWithImages(category, brand, removed, retained);
        ProductUpdateRequest request = updateRequest();
        request.setImageIds(java.util.List.of("m-retained", "m-new"));

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(mediaRepository.findAllByIdIn(request.getImageIds())).thenReturn(java.util.List.of(retained, added));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.updateProduct(1L, request);

        assertEquals(MediaStatus.PENDING_DELETE, removed.getStatus());
        assertEquals(MediaStatus.ACTIVE, retained.getStatus());
        assertEquals(MediaStatus.ACTIVE, added.getStatus());
        assertEquals(
                java.util.List.of("m-retained", "m-new"),
                product.getImages().stream()
                        .map(image -> image.getMedia().getId())
                        .toList());
        assertEquals(
                java.util.List.of(0, 1),
                product.getImages().stream()
                        .map(com.xdpsx.ecommerce.catalog.product.domain.ProductImage::getDisplayOrder)
                        .toList());
    }

    @Test
    void updateProduct_ShouldRejectInvalidMediaWithoutMutatingExistingState() {
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Media existing = media("m-existing", MediaStatus.ACTIVE);
        Product product = productWithImages(category, brand, existing);
        ProductUpdateRequest request = updateRequest();
        request.setImageIds(java.util.List.of("m-missing"));

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(mediaRepository.findAllByIdIn(request.getImageIds())).thenReturn(java.util.List.of());

        assertThrows(ApplicationException.class, () -> productService.updateProduct(1L, request));
        assertEquals(MediaStatus.ACTIVE, existing.getStatus());
        assertEquals(
                java.util.List.of("m-existing"),
                product.getImages().stream()
                        .map(image -> image.getMedia().getId())
                        .toList());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_ShouldRejectPublishingWithHiddenCategoryBeforeMutation() {
        Category hidden = Category.builder()
                .id(7)
                .name("Hidden")
                .status(CategoryStatus.INACTIVE)
                .build();
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Product product = new Product();
        product.setCategory(hidden);
        product.setBrand(brand);
        product.setPublished(false);
        product.setImages(new java.util.ArrayList<>());
        ProductUpdateRequest request = updateRequest();
        request.setPublished(true);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(hidden));

        assertThrows(ApplicationException.class, () -> productService.updateProduct(1L, request));
        assertThat(product.isPublished()).isFalse();
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_ShouldRequireAnActiveVariantBeforePublishing() {
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Product product = new Product();
        product.setCategory(category);
        product.setBrand(brand);
        product.setPublished(false);
        product.setImages(new java.util.ArrayList<>());
        ProductUpdateRequest request = updateRequest();
        request.setPublished(true);

        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findById(5)).thenReturn(Optional.of(brand));
        when(productVariantRepository.existsActiveByProductId(1L)).thenReturn(false);

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.updateProduct(1L, request));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getCode());
        assertThat(product.isPublished()).isFalse();
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_ShouldKeepProductAndMediaWhenOrderItemReferencesIt() {
        Category category = activeCategory(7);
        Brand brand = Brand.builder()
                .id(5)
                .name("Logitech")
                .status(BrandStatus.ACTIVE)
                .build();
        Media image = media("m-existing", MediaStatus.ACTIVE);
        Product product = productWithImages(category, brand, image);
        when(productRepository.findProductById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProductId(1L)).thenReturn(true);

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.deleteProduct(1L));

        assertEquals(ErrorCode.RESOURCE_IN_USE, exception.getCode());
        assertEquals(MediaStatus.ACTIVE, image.getStatus());
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void filterAllProducts_ShouldBatchLoadImagesOnceForTheWholePage() {
        Product first = new Product();
        first.setId(1L);
        Product second = new Product();
        second.setId(2L);
        @SuppressWarnings("unchecked")
        Page<Product> page = mock(Page.class);
        when(productSpecification.getFiltersSpec(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        when(productRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class)))
                .thenReturn(page);
        when(page.getContent()).thenReturn(List.of(first, second));
        ProductParams params = ProductParams.builder().build();

        productService.filterAllProducts(params);

        verify(productRepository).findAllWithImagesByIdIn(List.of(1L, 2L));
        verify(productMapper, never()).fromEntityToResponse(any(Product.class));
    }

    private static ProductUpdateRequest updateRequest() {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Keyboard");
        request.setSlug("keyboard");
        request.setPrice(BigDecimal.TEN);
        return request;
    }

    private static Product productWithImages(Category category, Brand brand, Media... media) {
        Product product = new Product();
        product.setCategory(category);
        product.setBrand(brand);
        product.setImages(new java.util.ArrayList<>());
        for (int index = 0; index < media.length; index++) {
            product.getImages()
                    .add(com.xdpsx.ecommerce.catalog.product.domain.ProductImage.builder()
                            .product(product)
                            .media(media[index])
                            .displayOrder(index)
                            .build());
        }
        return product;
    }

    private static Media media(String id, MediaStatus status) {
        return Media.builder()
                .id(id)
                .url("https://cdn/" + id)
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .status(status)
                .build();
    }
}
