package com.xdpsx.ecommerce.catalog.variantoption.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.api.dto.UpdateVariantOptionRequest;
import com.xdpsx.ecommerce.catalog.variantoption.api.dto.UpdateVariantOptionValueRequest;
import com.xdpsx.ecommerce.catalog.variantoption.domain.*;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class VariantOptionVariantReferenceTest {
    @Mock
    private VariantOptionRepository optionRepository;

    @Mock
    private VariantOptionValueRepository valueRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private VariantOptionServiceImpl service;

    @Test
    void deactivateOption_ShouldRejectWhenAnActiveVariantUsesIt() {
        VariantOption option = option(10L);
        when(optionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(option));
        when(productVariantRepository.existsActiveReferenceToOption(10L)).thenReturn(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.updateVariantOption(
                        10L, new UpdateVariantOptionRequest("Color", 0, VariantOptionStatus.INACTIVE)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE);
        verify(optionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivateValue_ShouldSucceedWhenNoActiveVariantUsesIt() {
        VariantOption option = option(10L);
        VariantOptionValue value = VariantOptionValue.builder()
                .id(20L)
                .option(option)
                .code("black")
                .name("Black")
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
        when(optionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(option));
        when(valueRepository.findByIdAndOptionIdForUpdate(20L, 10L)).thenReturn(Optional.of(value));
        when(productVariantRepository.existsActiveReferenceToValue(20L)).thenReturn(false);
        when(valueRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateValue(10L, 20L, new UpdateVariantOptionValueRequest("Black", 0, VariantOptionStatus.INACTIVE));

        assertThat(value.getStatus()).isEqualTo(VariantOptionStatus.INACTIVE);
        verify(valueRepository).saveAndFlush(value);
    }

    private static VariantOption option(Long id) {
        return VariantOption.builder()
                .id(id)
                .code("color")
                .name("Color")
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }
}
