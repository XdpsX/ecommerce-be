package com.xdpsx.ecommerce.refund.persistence;

public interface RefundLockTarget {
    Long getRefundId();

    Long getPaymentId();

    Long getOrderId();
}
