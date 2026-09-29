package com.xdpsx.ecommerce.inventory.domain;

/** Raised when an on-hand adjustment would violate the inventory balance invariant. */
public class InventoryBalanceAdjustmentException extends RuntimeException {
    public InventoryBalanceAdjustmentException(String message) {
        super(message);
    }

    public InventoryBalanceAdjustmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
