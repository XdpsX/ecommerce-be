package com.xdpsx.ecommerce.cart.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartItemMapper cartItemMapper;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final Clock pricingClock;

    @Override
    @Transactional
    public CartItemResponse addToCart(String userEmail, CartItemRequest request) {
        User user = getUser(userEmail);
        Instant now = now();
        ProductVariant variant = getVariant(request.getVariantId());
        CartItemId cartItemId = new CartItemId(user.getId(), variant.getId());
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElse(null);
        if (cartItem == null) {
            CartItem newCartItem = CartItem.builder()
                    .id(cartItemId)
                    .quantity(request.getQuantity())
                    .user(user)
                    .variant(variant)
                    .build();
            CartItem saved = cartItemRepository.save(newCartItem);
            saved.setAvailable(cartItemRepository
                    .findAvailableEligibleVariantIdsByUserId(user.getId())
                    .contains(variant.getId()));
            return cartItemMapper.fromEntityToResponse(saved, now);
        } else {
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
            CartItem saved = cartItemRepository.save(cartItem);
            saved.setAvailable(cartItemRepository
                    .findAvailableEligibleVariantIdsByUserId(user.getId())
                    .contains(variant.getId()));
            return cartItemMapper.fromEntityToResponse(saved, now);
        }
    }

    @Override
    @Transactional
    public void removeCartItem(String userEmail, Long variantId) {
        User user = getUser(userEmail);
        CartItemId cartItemId =
                CartItemId.builder().variantId(variantId).userId(user.getId()).build();
        CartItem cartItem = getCartItem(cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private CartItem getCartItem(CartItemId cartItemId) {
        return cartItemRepository
                .findById(cartItemId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("resourceType", "cartItem", "variantId", cartItemId.getVariantId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItemResponse> getCart(String userEmail) {
        User user = getUser(userEmail);
        Instant now = now();
        List<CartItem> cartItems = cartItemRepository.findNewestByUserId(user.getId());
        Set<Long> availableVariantIds =
                Set.copyOf(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()));
        cartItems.forEach(item ->
                item.setAvailable(availableVariantIds.contains(item.getVariant().getId())));
        return cartItems.stream()
                .map(item -> cartItemMapper.fromEntityToResponse(item, now))
                .toList();
    }

    @Override
    @Transactional
    public CartItemResponse updateCartItem(String userEmail, CartItemRequest request) {
        User user = getUser(userEmail);
        Instant now = now();
        CartItemId cartItemId = new CartItemId(user.getId(), request.getVariantId());
        CartItem cartItem = getCartItem(cartItemId);
        cartItem.setQuantity(request.getQuantity());
        CartItem saved = cartItemRepository.save(cartItem);
        saved.setAvailable(cartItemRepository
                .findAvailableEligibleVariantIdsByUserId(user.getId())
                .contains(request.getVariantId()));
        return cartItemMapper.fromEntityToResponse(saved, now);
    }

    @Override
    public long countCartItems(String userEmail) {
        User user = getUser(userEmail);
        return cartItemRepository.countByUserId(user.getId());
    }

    private ProductVariant getVariant(Long variantId) {
        ProductVariant variant = productVariantRepository
                .findEligibleStorefrontVariant(variantId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "variant", "resourceId", variantId)));
        return variant;
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(userEmail)
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user")));
    }

    private Instant now() {
        return (pricingClock == null ? Clock.systemUTC() : pricingClock).instant();
    }
}
