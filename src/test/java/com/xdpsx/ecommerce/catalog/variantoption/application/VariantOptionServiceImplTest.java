package com.xdpsx.ecommerce.catalog.variantoption.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.api.dto.*;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class VariantOptionServiceImplTest {
    @Mock
    private VariantOptionRepository optionRepository;

    @Mock
    private VariantOptionValueRepository valueRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @InjectMocks
    private VariantOptionServiceImpl service;

    @Test
    void createOption_ShouldCanonicalizeCodeAndPersistOrderedValues() {
        when(optionRepository.existsByCode("color")).thenReturn(false);
        when(optionRepository.saveAndFlush(any(VariantOption.class))).thenAnswer(invocation -> {
            VariantOption option = invocation.getArgument(0);
            option.setId(10L);
            return option;
        });
        when(optionRepository.findByIdWithValues(10L)).thenReturn(Optional.of(optionWithValues()));

        VariantOptionResponse result = service.createVariantOption(new CreateVariantOptionRequest(
                " Color ",
                " Colour ",
                2,
                null,
                List.of(
                        new CreateVariantOptionValueRequest(" BLACK ", "Black", null, null),
                        new CreateVariantOptionValueRequest("white", " White ", 1, VariantOptionStatus.INACTIVE))));

        assertThat(result.code()).isEqualTo("color");
        assertThat(result.status()).isEqualTo(VariantOptionStatus.ACTIVE);
        verify(valueRepository, times(2)).save(any(VariantOptionValue.class));
        verify(valueRepository).flush();
    }

    @Test
    void createOption_ShouldRejectCanonicalDuplicateWithoutSaving() {
        when(optionRepository.existsByCode("color")).thenReturn(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createVariantOption(
                        new CreateVariantOptionRequest(" COLOR ", "Colour", 0, VariantOptionStatus.ACTIVE, null)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
        verify(optionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createOption_ShouldRejectDuplicateValueCodeOrOrderInsideRequest() {
        when(optionRepository.existsByCode("color")).thenReturn(false);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createVariantOption(new CreateVariantOptionRequest(
                        "color",
                        "Color",
                        0,
                        VariantOptionStatus.ACTIVE,
                        List.of(
                                new CreateVariantOptionValueRequest("Black", "Black", 0, null),
                                new CreateVariantOptionValueRequest(" black ", "Other", 1, null)))));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
        verify(optionRepository, never()).saveAndFlush(any());
    }

    @Test
    void addValue_ShouldAppendAndNormalizeNameAndCode() {
        VariantOption option = optionWithValues();
        when(optionRepository.findByIdWithValues(10L)).thenReturn(Optional.of(option));
        when(valueRepository.existsByOptionIdAndCode(10L, "red")).thenReturn(false);
        when(valueRepository.saveAndFlush(any(VariantOptionValue.class))).thenAnswer(invocation -> {
            VariantOptionValue value = invocation.getArgument(0);
            value.setId(30L);
            return value;
        });

        VariantOptionValueResponse result =
                service.addValue(10L, new CreateVariantOptionValueRequest(" RED ", " Red ", null, null));

        assertThat(result.code()).isEqualTo("red");
        assertThat(result.name()).isEqualTo("Red");
        assertThat(result.displayOrder()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(VariantOptionStatus.ACTIVE);
    }

    @Test
    void updateValue_ShouldRejectDeactivationWhenAnActiveVariantReferencesIt() {
        VariantOption option = optionWithValues();
        VariantOptionValue value = option.getValues().get(0);
        when(optionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(option));
        when(valueRepository.findByIdAndOptionIdForUpdate(20L, 10L)).thenReturn(Optional.of(value));
        when(productVariantRepository.existsActiveReferenceToValue(20L)).thenReturn(true);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.updateValue(
                        10L, 20L, new UpdateVariantOptionValueRequest(" Jet Black ", 3, VariantOptionStatus.INACTIVE)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE);
        verify(valueRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateOption_ShouldRejectDeactivationWhenAnActiveVariantReferencesIt() {
        VariantOption option = optionWithValues();
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
    void reorderValues_ShouldSwapValuesWithTemporaryOrders() {
        VariantOption option = optionWithValues();
        when(optionRepository.findByIdWithValues(10L)).thenReturn(Optional.of(option));

        List<VariantOptionValueResponse> result =
                service.reorderValues(10L, new ReorderVariantOptionValuesRequest(List.of(21L, 20L)));

        assertThat(result).extracting(VariantOptionValueResponse::id).containsExactly(21L, 20L);
        assertThat(option.getValues())
                .extracting(VariantOptionValue::getDisplayOrder)
                .containsExactly(1, 0);
        verify(valueRepository, times(2)).flush();
    }

    @Test
    void createOption_ShouldTranslateDatabaseUniqueRace() {
        when(optionRepository.existsByCode("color")).thenReturn(false);
        doThrow(new DataIntegrityViolationException(
                        "duplicate key",
                        new ConstraintViolationException("duplicate key", null, "uk_variant_option_code")))
                .when(optionRepository)
                .saveAndFlush(any(VariantOption.class));

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> service.createVariantOption(
                        new CreateVariantOptionRequest("color", "Color", 0, VariantOptionStatus.ACTIVE, null)));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_ALREADY_EXISTS);
    }

    private static VariantOption optionWithValues() {
        VariantOption option = VariantOption.builder()
                .id(10L)
                .code("color")
                .name("Color")
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
        VariantOptionValue value = VariantOptionValue.builder()
                .id(20L)
                .option(option)
                .code("black")
                .name("Black")
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
        VariantOptionValue second = VariantOptionValue.builder()
                .id(21L)
                .option(option)
                .code("white")
                .name("White")
                .displayOrder(1)
                .status(VariantOptionStatus.ACTIVE)
                .build();
        option.setValues(new java.util.ArrayList<>(List.of(value, second)));
        return option;
    }
}
