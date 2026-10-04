package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
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
    void createProduct_ShouldNormalizeSlugBeforeCheckingAndSavingIt() {
        Category category = activeCategory(7);
        Brand brand = activeBrand(5);
        Product product = new Product();
        ProductCreateRequest request = createRequest(7);
        request.setSlug(" MÁy Keyboard ");
        when(productRepository.existsBySlug("may-keyboard")).thenReturn(false);
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(category));
        when(brandRepository.findById(5)).thenReturn(Optional.of(brand));
        when(productMapper.fromCreateRequestToEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);

        productService.createProduct(request);

        assertThat(product.getSlug()).isEqualTo("may-keyboard");
        verify(productRepository).existsBySlug("may-keyboard");
    }

    @Test
    void createProduct_ShouldRejectAnEmptyCanonicalSlug() {
        ProductCreateRequest request = createRequest(7);
        request.setSlug("!!!");

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.createProduct(request));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_PRODUCT_SLUG);
        verifyNoInteractions(productRepository, categoryRepository, brandRepository);
    }

    @Test
    void getSlugAvailability_ShouldCheckTheCanonicalSlug() {
        when(productRepository.existsBySlug("may-keyboard")).thenReturn(false);

        assertThat(productService.getSlugAvailability(" MÁy Keyboard ")).containsEntry("slugExists", false);

        verify(productRepository).existsBySlug("may-keyboard");
    }

    @Test
    void updateProduct_ShouldPreserveAnExistingLegacySlugWhenResubmittedUnchanged() {
        Product product = product(1L, activeCategory(7), activeBrand(5));
        product.setSlug("Legacy Product URL");
        ProductUpdateRequest request = updateRequest();
        request.setSlug("Legacy Product URL");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        productService.updateProduct(1L, request);

        assertThat(product.getSlug()).isEqualTo("Legacy Product URL");
        verify(productRepository, never()).existsBySlug(any());
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
        when(productVariantRepository.findAdminPriceRanges(eq(List.of(1L)), any(Instant.class)))
                .thenReturn(List.of());
        when(inventoryBalanceRepository.findActiveProductInventoryTotals(List.of(1L)))
                .thenReturn(List.of());

        productService.updateProduct(1L, request);

        assertThat(product.isPublished()).isTrue();
        verify(productRepository).save(product);
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
        when(inventoryBalanceRepository.findAvailableStorefrontVariantIdsByProductId(1L))
                .thenReturn(List.of(10L));
        when(productMapper.toStorefrontDetail(
                        eq(product), any(BigDecimal.class), any(BigDecimal.class), anyList(), anyList(), eq(true)))
                .thenReturn(null);

        productService.getStorefrontProductBySlug("available");

        verify(productMapper)
                .toStorefrontDetail(
                        eq(product),
                        any(BigDecimal.class),
                        any(BigDecimal.class),
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
                        any(), any(), any(), any(), any(), any(), any(), any(), any(Instant.class)))
                .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(page.getContent()).thenReturn(List.of(first, second));
        when(productVariantRepository.findEligiblePriceRanges(eq(List.of(1L, 2L)), any(Instant.class)))
                .thenReturn(List.of());
        when(inventoryBalanceRepository.findAvailableStorefrontProductIdsByProductIds(List.of(1L, 2L)))
                .thenReturn(List.of());
        when(productMapper.toStorefrontSummary(
                        any(Product.class), anyBoolean(), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new StorefrontProductSummaryResponse(
                        1L, "Product", "product", BigDecimal.TEN, BigDecimal.TEN, "VND", true, null, null, null));

        productService.getStorefrontProducts(StorefrontProductFilter.builder().build());

        verify(productRepository).findAllWithImagesByIdIn(List.of(1L, 2L));
        verify(productVariantRepository, times(1)).findEligiblePriceRanges(eq(List.of(1L, 2L)), any(Instant.class));
        verify(inventoryBalanceRepository, times(1)).findAvailableStorefrontProductIdsByProductIds(List.of(1L, 2L));
        verify(productMapper)
                .toStorefrontSummary(first, false, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        verify(productMapper)
                .toStorefrontSummary(second, false, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
    }

    @Test
    void getAdminProducts_ShouldUsePublicationFilterWithoutStorefrontVisibility() {
        Product draft = product(1L, activeCategory(7), activeBrand(5));
        ProductVariantRepository.PriceRangeView priceRange = new ProductVariantRepository.PriceRangeView() {
            @Override
            public Long getProductId() {
                return 1L;
            }

            @Override
            public BigDecimal getMinimumPrice() {
                return new BigDecimal("10.00");
            }

            @Override
            public BigDecimal getMaximumPrice() {
                return new BigDecimal("30.00");
            }
        };
        InventoryBalanceRepository.ProductInventoryTotals totals =
                new InventoryBalanceRepository.ProductInventoryTotals() {
                    @Override
                    public Long getProductId() {
                        return 1L;
                    }

                    @Override
                    public Long getOnHand() {
                        return 15L;
                    }

                    @Override
                    public Long getReserved() {
                        return 4L;
                    }

                    @Override
                    public Long getAvailable() {
                        return 11L;
                    }
                };
        @SuppressWarnings("unchecked")
        Page<Product> page = mock(Page.class);
        when(productSpecification.getAdminFiltersSpec(eq("draft"), eq("name"), eq(false), any(Instant.class)))
                .thenReturn((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(page.getContent()).thenReturn(List.of(draft));
        when(productVariantRepository.findAdminPriceRanges(eq(List.of(1L)), any(Instant.class)))
                .thenReturn(List.of(priceRange));
        when(inventoryBalanceRepository.findActiveProductInventoryTotals(List.of(1L)))
                .thenReturn(List.of(totals));
        when(productMapper.toAdminSummary(
                        eq(draft),
                        eq(true),
                        eq(new BigDecimal("10.00")),
                        eq(new BigDecimal("30.00")),
                        eq(15L),
                        eq(4L),
                        eq(11L)))
                .thenReturn(new AdminProductSummaryResponse(
                        1L,
                        "Draft",
                        "draft",
                        true,
                        false,
                        null,
                        null,
                        null,
                        new BigDecimal("10.00"),
                        new BigDecimal("30.00"),
                        15L,
                        4L,
                        11L));

        productService.getAdminProducts(AdminProductFilter.builder()
                .search("draft")
                .sort("name")
                .hasPublished(false)
                .build());

        verify(productSpecification).getAdminFiltersSpec(eq("draft"), eq("name"), eq(false), any(Instant.class));
        verify(productSpecification, never()).storefrontVisibility();
        verify(productVariantRepository).findAdminPriceRanges(eq(List.of(1L)), any(Instant.class));
        verify(inventoryBalanceRepository).findActiveProductInventoryTotals(List.of(1L));
        verify(productMapper)
                .toAdminSummary(draft, true, new BigDecimal("10.00"), new BigDecimal("30.00"), 15L, 4L, 11L);
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
