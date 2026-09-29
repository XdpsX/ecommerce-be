package com.xdpsx.ecommerce.inventory.application;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;
import com.xdpsx.ecommerce.inventory.domain.InventoryAdjustment;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalanceAdjustmentException;
import com.xdpsx.ecommerce.inventory.persistence.InventoryAdjustmentRepository;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;

    @Transactional(readOnly = true)
    @Override
    public InventoryBalanceResponse getBalance(Long variantId) {
        InventoryBalance balance =
                inventoryBalanceRepository.findByVariantIdWithVariant(variantId).orElseThrow(() -> notFound(variantId));
        return toResponse(balance);
    }

    @Transactional
    @Override
    public InventoryBalanceResponse adjustOnHand(Long variantId, InventoryAdjustmentRequest request, String actor) {
        if (request == null || request.quantityDelta() == null) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "quantityDelta", "reason", "quantityDelta is required"));
        }
        if (request.reason() == null) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED, Map.of("field", "reason", "reason", "reason is required"));
        }
        long quantityDelta = request.quantityDelta();
        if (quantityDelta == 0) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "quantityDelta", "reason", "quantityDelta must not be zero"));
        }
        String reason = request.reason().trim();
        if (reason.isEmpty() || reason.length() > 500) {
            throw new ApplicationException(
                    ErrorCode.VALIDATION_FAILED,
                    Map.of("field", "reason", "reason", "reason must contain 1 to 500 characters after trimming"));
        }
        String performedBy = normalizeActor(actor);
        InventoryBalance balance =
                inventoryBalanceRepository.findByVariantIdForUpdate(variantId).orElseThrow(() -> notFound(variantId));
        try {
            balance.adjustOnHand(quantityDelta);
        } catch (InventoryBalanceAdjustmentException exception) {
            throw new ApplicationException(
                    ErrorCode.INVENTORY_ADJUSTMENT_REJECTED,
                    Map.of("variantId", variantId, "quantityDelta", quantityDelta),
                    exception);
        }

        inventoryBalanceRepository.saveAndFlush(balance);
        inventoryAdjustmentRepository.saveAndFlush(new InventoryAdjustment(
                variantId, quantityDelta, reason, performedBy, balance.getOnHand(), balance.getReserved()));
        return toResponse(balance);
    }

    private static InventoryBalanceResponse toResponse(InventoryBalance balance) {
        return new InventoryBalanceResponse(
                balance.getVariantId(),
                balance.getVariant().getSku(),
                balance.getOnHand(),
                balance.getReserved(),
                balance.available());
    }

    private static ApplicationException notFound(Long variantId) {
        return new ApplicationException(
                ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "inventory-balance", "variantId", variantId));
    }

    private static String normalizeActor(String actor) {
        if (actor == null) {
            throw invalidActor("actor is required");
        }
        String normalized = actor.trim();
        if (normalized.isEmpty() || normalized.length() > InventoryAdjustment.MAX_PERFORMED_BY_LENGTH) {
            throw invalidActor("actor must contain 1 to 320 characters after trimming");
        }
        return normalized;
    }

    private static ApplicationException invalidActor(String reason) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", "actor", "reason", reason));
    }
}
