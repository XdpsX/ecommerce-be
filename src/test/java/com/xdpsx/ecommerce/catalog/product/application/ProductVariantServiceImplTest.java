package com.xdpsx.ecommerce.catalog.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.product.api.dto.*;
import com.xdpsx.ecommerce.catalog.product.domain.*;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.domain.*;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.application.InventoryProvisioningService;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private VariantOptionRepository optionRepository;

    @Mock
    private VariantOptionValueRepository optionValueRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private InventoryProvisioningService inventoryProvisioningService;

    @InjectMocks
    private ProductVariantServiceImpl service;

    @Captor
    private ArgumentCaptor<List<ProductVariant>> captor;

    @Test
    void createVariants_ShouldPersistAnOptionMatrixWithStableCombinationKeys() {
        Product product = Product.builder().id(1L).name("Shirt").slug("shirt").build();
        VariantOption color = option(10L, "color");
        VariantOption size = option(20L, "size");
        VariantOptionValue black = value(101L, color, "black");
        VariantOptionValue white = value(102L, color, "white");
        VariantOptionValue medium = value(201L, size, "medium");
        VariantOptionValue large = value(202L, size, "large");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(optionValueRepository.findAllByIdInWithOptionForUpdate(any()))
                .thenReturn(List.of(black, white, medium, large));
        when(variantRepository.findAllWithSelectionsByProductId(1L)).thenReturn(List.of());
        when(variantRepository.existsBySku(any())).thenReturn(false);
        when(variantRepository.existsByProductIdAndCombinationKey(any(), any())).thenReturn(false);

        ProductVariantBatchCreateRequest request = new ProductVariantBatchCreateRequest(List.of(
                new ProductVariantCreateRequest(" shirt-black-m ", null, List.of(101L, 201L)),
                new ProductVariantCreateRequest("shirt-black-l", null, List.of(101L, 202L)),
                new ProductVariantCreateRequest("shirt-white-m", null, List.of(102L, 201L)),
                new ProductVariantCreateRequest("shirt-white-l", null, List.of(102L, 202L))));

        service.createVariants(1L, request);

        verify(variantRepository).saveAll(captor.capture());
        verify(inventoryProvisioningService).provisionBalances(captor.getValue());
        assertThat(captor.getValue()).hasSize(4);
        assertThat(captor.getValue().get(0).getSku()).isEqualTo("SHIRT-BLACK-M");
        assertThat(captor.getValue().get(0).getCombinationKey()).isEqualTo("10=101|20=201");
        assertThat(captor.getValue().get(0).getSelections())
                .extracting(selection -> selection.getId().getOptionId())
                .containsExactly(10L, 20L);
    }

    @Test
    void createVariants_ShouldRejectDuplicateCombinationBeforeSavingAnyVariant() {
        Product product = Product.builder().id(1L).name("Shirt").slug("shirt").build();
        VariantOption color = option(10L, "color");
        VariantOptionValue black = value(101L, color, "black");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(optionValueRepository.findAllByIdInWithOptionForUpdate(any())).thenReturn(List.of(black));
        when(variantRepository.findAllWithSelectionsByProductId(1L)).thenReturn(List.of());
        when(variantRepository.existsBySku(any())).thenReturn(false);

        ProductVariantBatchCreateRequest request = new ProductVariantBatchCreateRequest(List.of(
                new ProductVariantCreateRequest("one", null, List.of(101L)),
                new ProductVariantCreateRequest("two", null, List.of(101L))));

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> service.createVariants(1L, request));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
        verify(variantRepository, never()).saveAll(any());
    }

    @Test
    void createVariants_ShouldRejectTwoValuesFromOneOption() {
        Product product = Product.builder().id(1L).name("Shirt").slug("shirt").build();
        VariantOption color = option(10L, "color");
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(optionValueRepository.findAllByIdInWithOptionForUpdate(any()))
                .thenReturn(List.of(value(101L, color, "black"), value(102L, color, "white")));
        when(variantRepository.findAllWithSelectionsByProductId(1L)).thenReturn(List.of());
        when(variantRepository.existsBySku(any())).thenReturn(false);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createVariants(
                        1L,
                        new ProductVariantBatchCreateRequest(
                                List.of(new ProductVariantCreateRequest("invalid", null, List.of(101L, 102L))))));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(variantRepository, never()).saveAll(any());
    }

    @Test
    void updateStatus_ShouldKeepAnActiveVariantForPublishedProduct() {
        Product product = Product.builder()
                .id(1L)
                .name("Shirt")
                .slug("shirt")
                .published(true)
                .build();
        ProductVariant variant = ProductVariant.builder()
                .id(8L)
                .product(product)
                .sku("SHIRT")
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey("")
                .build();
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductIdWithSelections(8L, 1L)).thenReturn(Optional.of(variant));
        when(variantRepository.countByProductIdAndStatus(1L, ProductVariantStatus.ACTIVE))
                .thenReturn(1L);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.updateStatus(
                        1L, 8L, new UpdateProductVariantStatusRequest(ProductVariantStatus.INACTIVE)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE);
        assertThat(variant.getStatus()).isEqualTo(ProductVariantStatus.ACTIVE);
        verify(variantRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateStatus_ShouldRejectReactivationWithDifferentActiveOptionSet() {
        Product product = Product.builder().id(1L).name("Shirt").slug("shirt").build();
        VariantOption color = option(10L, "color");
        VariantOption size = option(20L, "size");
        VariantOptionValue black = value(101L, color, "black");
        VariantOptionValue medium = value(201L, size, "medium");
        ProductVariant inactive = ProductVariant.builder()
                .id(8L)
                .product(product)
                .sku("SHIRT-BLACK")
                .status(ProductVariantStatus.INACTIVE)
                .combinationKey("10=101")
                .build();
        inactive.getSelections().add(selection(inactive, color, black));
        ProductVariant active = ProductVariant.builder()
                .id(9L)
                .product(product)
                .sku("SHIRT-BLACK-M")
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey("10=101|20=201")
                .build();
        active.getSelections().add(selection(active, color, black));
        active.getSelections().add(selection(active, size, medium));
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(variantRepository.findByIdAndProductIdWithSelections(8L, 1L)).thenReturn(Optional.of(inactive));
        when(variantRepository.findAllWithSelectionsByProductId(1L)).thenReturn(List.of(inactive, active));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.updateStatus(1L, 8L, new UpdateProductVariantStatusRequest(ProductVariantStatus.ACTIVE)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(variantRepository, never()).saveAndFlush(any());
    }

    private static VariantOption option(Long id, String code) {
        return VariantOption.builder()
                .id(id)
                .code(code)
                .name(code)
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static VariantOptionValue value(Long id, VariantOption option, String code) {
        return VariantOptionValue.builder()
                .id(id)
                .option(option)
                .code(code)
                .name(code)
                .displayOrder(id.intValue())
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static ProductVariantSelection selection(
            ProductVariant variant, VariantOption option, VariantOptionValue value) {
        return ProductVariantSelection.builder()
                .id(new ProductVariantSelectionId(variant.getId(), option.getId()))
                .variant(variant)
                .optionValueId(value.getId())
                .optionValue(value)
                .build();
    }
}
