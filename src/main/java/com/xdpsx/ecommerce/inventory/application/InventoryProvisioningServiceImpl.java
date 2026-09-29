package com.xdpsx.ecommerce.inventory.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryProvisioningServiceImpl implements InventoryProvisioningService {
    private final InventoryBalanceRepository inventoryBalanceRepository;

    @Override
    public void provisionBalances(List<ProductVariant> variants) {
        if (variants == null || variants.isEmpty()) return;
        inventoryBalanceRepository.saveAll(
                variants.stream().map(InventoryBalance::zero).toList());
        inventoryBalanceRepository.flush();
    }
}
