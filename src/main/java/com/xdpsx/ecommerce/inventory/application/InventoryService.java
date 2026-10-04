package com.xdpsx.ecommerce.inventory.application;

import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;

public interface InventoryService {
    InventoryBalanceResponse getBalance(Long variantId);

    InventoryBalanceResponse adjustOnHand(Long variantId, InventoryAdjustmentRequest request, String actor);
}
