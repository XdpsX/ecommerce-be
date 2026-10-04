package com.xdpsx.ecommerce.checkout.application;

import com.xdpsx.ecommerce.order.domain.Order;

record CheckoutTransactionResult(Order order, boolean replayed) {}
