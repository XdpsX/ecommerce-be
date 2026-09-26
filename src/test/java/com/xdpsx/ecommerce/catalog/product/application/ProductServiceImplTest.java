package com.xdpsx.ecommerce.catalog.product.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductCreateRequest;
import com.xdpsx.ecommerce.catalog.product.api.dto.ProductUpdateRequest;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

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
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

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
        Brand brand = Brand.builder().id(5).name("Logitech").build();
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

        when(productRepository.findProductById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findByIdWithAncestry(7)).thenReturn(Optional.of(hidden));

        // Act & Assert
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> productService.updateProduct(1L, request));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
        verify(productRepository, never()).save(any(Product.class));
        // The stored assignment is untouched by a rejected reassignment.
        assertSame(current, product.getCategory());
    }
}
