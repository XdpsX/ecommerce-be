package com.xdpsx.ecommerce.controllers;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.dtos.cart.CartItemRequest;
import com.xdpsx.ecommerce.dtos.cart.CartItemResponse;
import com.xdpsx.ecommerce.services.CartService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @PostMapping("/add")
    public ResponseEntity<CartItemResponse> addToCart(
            @Valid @RequestBody CartItemRequest request, Authentication authentication) {
        CartItemResponse response = cartService.addToCart(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<Void> removeCartItem(@PathVariable Long productId, Authentication authentication) {
        cartService.removeCartItem(authentication.getName(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCart(Authentication authentication) {
        List<CartItemResponse> response = cartService.getCart(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/update")
    public ResponseEntity<CartItemResponse> updateCart(
            @Valid @RequestBody CartItemRequest request, Authentication authentication) {
        CartItemResponse response = cartService.updateCartItem(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> countCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.countCartItems(authentication.getName()));
    }
}
