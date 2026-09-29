package com.xdpsx.ecommerce.inventory.api.dto;

public record InventoryBalanceResponse(Long variantId, String sku, long onHand, long reserved, long available) {}
