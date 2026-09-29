package com.xdpsx.ecommerce.inventory.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;
import com.xdpsx.ecommerce.inventory.application.InventoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/inventory/variants/{variantId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminInventoryController implements AdminInventoryApiDocs {
    private final InventoryService inventoryService;

    @GetMapping
    @Override
    public InventoryBalanceResponse getBalance(@PathVariable Long variantId) {
        return inventoryService.getBalance(variantId);
    }

    @PostMapping("/adjustments")
    @Override
    public InventoryBalanceResponse adjustOnHand(
            @PathVariable Long variantId,
            @Valid @RequestBody InventoryAdjustmentRequest request,
            Authentication authentication) {
        return inventoryService.adjustOnHand(variantId, request, authentication.getName());
    }
}
