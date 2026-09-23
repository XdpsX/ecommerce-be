package com.xdpsx.ecommerce.cart.application;

import java.util.List;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;

public interface CartService {
    CartItemResponse addToCart(String userEmail, CartItemRequest request);

    void removeCartItem(String userEmail, Long productId);

    List<CartItemResponse> getCart(String userEmail);

    CartItemResponse updateCartItem(String userEmail, CartItemRequest request);

    long countCartItems(String userEmail);
}
