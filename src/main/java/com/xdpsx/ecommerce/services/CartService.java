package com.xdpsx.ecommerce.services;

import java.util.List;

import com.xdpsx.ecommerce.dtos.cart.CartItemRequest;
import com.xdpsx.ecommerce.dtos.cart.CartItemResponse;

public interface CartService {
    CartItemResponse addToCart(String userEmail, CartItemRequest request);

    void removeCartItem(String userEmail, Long productId);

    List<CartItemResponse> getCart(String userEmail);

    CartItemResponse updateCartItem(String userEmail, CartItemRequest request);

    long countCartItems(String userEmail);
}
