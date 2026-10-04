package com.xdpsx.ecommerce.cart.domain;

public class CartQuantityException extends RuntimeException {
    public CartQuantityException(String message) {
        super(message);
    }
}
