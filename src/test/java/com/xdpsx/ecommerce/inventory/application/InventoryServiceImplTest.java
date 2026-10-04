package com.xdpsx.ecommerce.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.domain.InventoryAdjustment;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryAdjustmentRepository;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {
    @Mock
    private InventoryBalanceRepository balanceRepository;

    @Mock
    private InventoryAdjustmentRepository adjustmentRepository;

    @Test
    void adjustOnHand_ShouldUpdateBalanceAndWriteAuditSnapshot() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        when(balanceRepository.findByVariantIdForUpdate(10L)).thenReturn(Optional.of(balance));
        InventoryService service = new InventoryServiceImpl(balanceRepository, adjustmentRepository);

        var response =
                service.adjustOnHand(10L, new InventoryAdjustmentRequest(7L, "  Initial receipt  "), "admin@test");

        assertThat(response)
                .isEqualTo(new com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse(10L, "SKU-10", 7, 0, 7));
        verify(balanceRepository).saveAndFlush(balance);
        ArgumentCaptor<InventoryAdjustment> captor = ArgumentCaptor.forClass(InventoryAdjustment.class);
        verify(adjustmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getReason()).isEqualTo("Initial receipt");
        assertThat(captor.getValue().getPerformedBy()).isEqualTo("admin@test");
        assertThat(captor.getValue().getOnHandAfter()).isEqualTo(7);
    }

    @Test
    void adjustOnHand_ShouldRejectInvariantViolationWithoutWritingAudit() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(2);
        when(balanceRepository.findByVariantIdForUpdate(10L)).thenReturn(Optional.of(balance));
        InventoryService service = new InventoryServiceImpl(balanceRepository, adjustmentRepository);

        assertThatThrownBy(
                        () -> service.adjustOnHand(10L, new InventoryAdjustmentRequest(-3L, "shrinkage"), "admin@test"))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.INVENTORY_ADJUSTMENT_REJECTED));
        verify(balanceRepository, never()).saveAndFlush(any());
        verifyNoInteractions(adjustmentRepository);
    }

    @Test
    void adjustOnHand_ShouldRejectMissingActorBeforeMutatingBalance() {
        ProductVariant variant = ProductVariant.builder().id(10L).sku("SKU-10").build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(2);
        InventoryService service = new InventoryServiceImpl(balanceRepository, adjustmentRepository);

        assertThatThrownBy(() -> service.adjustOnHand(10L, new InventoryAdjustmentRequest(3L, "receipt"), null))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(balance.getOnHand()).isEqualTo(2);
        verifyNoInteractions(balanceRepository, adjustmentRepository);
    }
}
