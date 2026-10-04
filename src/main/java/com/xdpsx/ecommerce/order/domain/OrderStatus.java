package com.xdpsx.ecommerce.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    PAYMENT_EXPIRED,
    /** @deprecated retained so historical rows remain readable after migration. */
    @Deprecated
    PENDING,
    CANCELLED
}
