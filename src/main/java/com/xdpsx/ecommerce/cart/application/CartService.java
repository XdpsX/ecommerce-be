package com.xdpsx.ecommerce.cart.application;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;

public interface CartService {
    CartResponse getCartForCustomer(String userEmail);

    CartResponse addItem(String userEmail, CartItemRequest request);

    CartResponse replaceItem(String userEmail, Long variantId, CartQuantityRequest request);

    CartResponse removeItem(String userEmail, Long variantId);
}
