package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductSpecification;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;
import com.xdpsx.ecommerce.order.persistence.OrderItemRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductSpecification productSpecification;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private VariantOptionValueRepository variantOptionValueRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void createProduct_ShouldAlwaysCreateAnUnpublishedDraft() {
        Category category = activeCategory(7);
        Brand brand = activeBrand(5);
        Product product = new Product();
        when(productRepository.existsBySlug("keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findById(5)).thenReturn(Optional.of(brand));
        when(productMapper.fromCreateRequestToEntity(any())).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);

        productService.createProduct(createRequest(7));

        assertThat(product.isPublished()).isFalse();
        verify(productMapper).toAdminSummary(product);
    }

    @Test
    void createProduct_ShouldRejectInactiveAncestorCategory() {
        Category parent =
                Category.builder().id(1).status(CategoryStatus.INACTIVE).build();
        Category hidden = Category.builder()
                .id(7)
                .status(CategoryStatus.ACTIVE)
                .parent(parent)
                .build();
        when(productRepository.existsBySlug("keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(hidden));

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.createProduct(createRequest(7)));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_ShouldNotChangePublicationState() {
        Product product = product(1L, activeCategory(7), activeBrand(5));
        product.setPublished(true);
        ProductUpdateRequest request = updateRequest();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.updateProduct(1L, request);

        assertThat(product.isPublished()).isTrue();
        verify(productRepository).save(product);
        verifyNoInteractions(productVariantRepository);
    }

    @Test
    void updatePublication_ShouldRejectWithoutActiveVariant() {
        Product product = product(1L, activeCategory(7), activeBrand(5));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(product.getCategory()));
        when(brandRepository.findById(5)).thenReturn(Optional.of(product.getBrand()));
        when(productVariantRepository.existsActiveByProductId(1L)).thenReturn(false);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> productService.updatePublication(1L, new UpdateProductPublicationRequest(true)));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getCode());
        assertThat(product.isPublished()).isFalse();
        verify(productRepository, never()).save(any());
    }

    @Test
    void getStorefrontProductBySlug_ShouldHideUnpublishedProduct() {
        when(productRepository.findStorefrontProductBySlug("draft")).thenReturn(Optional.empty());

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.getStorefrontProductBySlug("draft"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
    }

    @Test
    void getStorefrontProductBySlug_ShouldDeriveProductAndVariantAvailabilityFromInventory() {
        Product product = product(1L, activeCategory(7), activeBrand(5));
        ProductVariant variant = ProductVariant.builder()
                .id(10L)
                .product(product)
                .sku("SKU-10")
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey("")
                .build();
        when(productRepository.findStorefrontProductBySlug("available")).thenReturn(Optional.of(product));
        when(productVariantRepository.findActiveWithSelectionsAndOptionsByProductId(1L))
                .thenReturn(List.of(variant));
        when(inventoryBalanceRepository.findAvailableVariantIdsByProductId(1L)).thenReturn(List.of(10L));
        when(productMapper.toStorefrontDetail(eq(product), anyList(), anyList(), eq(true)))
                .thenReturn(null);

        productService.getStorefrontProductBySlug("available");

        verify(productMapper)
                .toStorefrontDetail(
                        eq(product),
                        anyList(),
                        argThat(variants -> variants.get(0).available()),
                        eq(true));
    }

    @Test
    void getStorefrontProducts_ShouldBatchLoadImagesAndUseStorefrontSpecification() {
        Product first = product(1L, activeCategory(7), activeBrand(5));
        Product second = product(2L, activeCategory(7), activeBrand(5));
        @SuppressWarnings("unchecked")
        Page<Product> page = mock(Page.class);
        when(productSpecification.getStorefrontFiltersSpec(
                        any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(page.getContent()).thenReturn(List.of(first, second));
        when(productMapper.toStorefrontSummary(any(Product.class), anyBoolean()))
                .thenReturn(new StorefrontProductSummaryResponse(
                        1L, "Product", "product", BigDecimal.TEN, null, 0, true, null, null, null));

        productService.getStorefrontProducts(StorefrontProductFilter.builder().build());

        verify(productRepository).findAllWithImagesByIdIn(List.of(1L, 2L));
        verify(productMapper).toStorefrontSummary(first, false);
        verify(productMapper).toStorefrontSummary(second, false);
    }

    @Test
    void getAdminProducts_ShouldUsePublicationFilterWithoutStorefrontVisibility() {
        @SuppressWarnings("unchecked")
        Page<Product> page = mock(Page.class);
        when(productSpecification.getAdminFiltersSpec("draft", "name", false))
                .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(page.getContent()).thenReturn(List.of());

        productService.getAdminProducts(AdminProductFilter.builder()
                .search("draft")
                .sort("name")
                .hasPublished(false)
                .build());

        verify(productSpecification).getAdminFiltersSpec("draft", "name", false);
        verify(productSpecification, never()).storefrontVisibility();
    }

    @Test
    void getStorefrontProducts_ShouldRejectDuplicateOptionValuesBeforeProductQuery() {
        StorefrontProductFilter filter = StorefrontProductFilter.builder()
                .optionValueIds(List.of(101L, 101L))
                .build();

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.getStorefrontProducts(filter));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(variantOptionValueRepository, productRepository);
    }

    private static ProductCreateRequest createRequest(Integer categoryId) {
        return ProductCreateRequest.builder()
                .name("Keyboard")
                .slug("keyboard")
                .categoryId(categoryId)
                .brandId(5)
                .build();
    }

    private static ProductUpdateRequest updateRequest() {
        ProductUpdateRequest request = new ProductUpdateRequest();
        request.setName("Keyboard");
        request.setSlug("keyboard");
        return request;
    }

    private static Product product(Long id, Category category, Brand brand) {
        Product product = new Product();
        product.setId(id);
        product.setCategory(category);
        product.setBrand(brand);
        product.setImages(new ArrayList<>());
        return product;
    }

    private static Category activeCategory(Integer id) {
        return Category.builder()
                .id(id)
                .name("Category " + id)
                .slug("category-" + id)
                .status(CategoryStatus.ACTIVE)
                .displayOrder(0)
                .build();
    }

    private static Brand activeBrand(Integer id) {
        return Brand.builder()
                .id(id)
                .name("Brand " + id)
                .status(BrandStatus.ACTIVE)
                .build();
    }
}
