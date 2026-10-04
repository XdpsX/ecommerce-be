package com.xdpsx.ecommerce.inventory.application;

import java.util.List;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;

public interface InventoryProvisioningService {
    void provisionBalances(List<ProductVariant> variants);
}
