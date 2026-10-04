package com.xdpsx.ecommerce.cart.application;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;

public interface CartService {
    CartResponse getCart(CartOwner owner);

    CartMutationResult addItem(CartOwner owner, CartItemRequest request);

    CartMutationResult replaceItem(CartOwner owner, Long variantId, CartQuantityRequest request);

    CartMutationResult removeItem(CartOwner owner, Long variantId);

    CartMutationResult claimGuestCart(String userEmail, String guestCredential);
}
