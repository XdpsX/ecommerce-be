package com.xdpsx.ecommerce.cart.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.cart.api.dto.CartAvailability;
import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.api.dto.CartQuantityRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartResponse;
import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartItemMapper cartItemMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StorePricingProperties pricingProperties;
    private final Clock pricingClock;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCartForCustomer(String userEmail) {
        User user = getUser(userEmail);
        return readCart(cartRepository.findByUserId(user.getId()).orElse(null));
    }

    @Override
    @Transactional
    public CartResponse addItem(String userEmail, CartItemRequest request) {
        validateRequest(request.getQuantity());
        Cart cart = getOrCreateCustomerCart(userEmail);
        ProductVariant variant = getSaleableVariant(request.getVariantId());
        CartItem item = cartItemRepository
                .findByCartIdAndVariantId(cart.getId(), variant.getId())
                .orElse(null);
        int desired = item == null ? request.getQuantity() : safeSum(item.getQuantity(), request.getQuantity());
        if (desired > CartItem.MAX_QUANTITY) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "invalidQuantity"));
        }
        ensureAvailable(variant.getId(), desired);
        if (item == null) {
            item = CartItem.builder()
                    .id(new CartItemId(cart.getId(), variant.getId()))
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .build();
        } else {
            item.increaseBy(request.getQuantity());
        }
        cartItemRepository.save(item);
        return readCart(cart);
    }

    @Override
    @Transactional
    public CartResponse replaceItem(String userEmail, Long variantId, CartQuantityRequest request) {
        validateRequest(request.getQuantity());
        Cart cart = getCustomerCartForWrite(userEmail);
        CartItem item = getCartItem(cart, variantId);
        ProductVariant variant = getSaleableVariant(variantId);
        ensureAvailable(variant.getId(), request.getQuantity());
        item.replaceQuantity(request.getQuantity());
        return readCart(cart);
    }

    @Override
    @Transactional
    public CartResponse removeItem(String userEmail, Long variantId) {
        Cart cart = getCustomerCartForWrite(userEmail);
        CartItem item = getCartItem(cart, variantId);
        cartItemRepository.delete(item);
        return readCart(cart);
    }

    private Cart getOrCreateCustomerCart(String userEmail) {
        User user = getUser(userEmail);
        Optional<Cart> existing = cartRepository.findByUserIdForUpdate(user.getId());
        if (existing.isPresent()) return existing.get();
        User lockedUser = userRepository.findByIdForUpdate(user.getId()).orElseThrow();
        return cartRepository
                .findByUserIdForUpdate(lockedUser.getId())
                .orElseGet(() -> cartRepository.save(Cart.forCustomer(lockedUser)));
    }

    private Cart getCustomerCartForWrite(String userEmail) {
        User user = getUser(userEmail);
        return cartRepository
                .findByUserIdForUpdate(user.getId())
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "cart")));
    }

    private CartItem getCartItem(Cart cart, Long variantId) {
        return cartItemRepository
                .findByCartIdAndVariantId(cart.getId(), variantId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "cartItem", "variantId", variantId)));
    }

    private CartResponse readCart(Cart cart) {
        if (cart == null) return emptyCart();
        List<CartItem> items = cartItemRepository.findByCartIdWithCatalog(cart.getId());
        Map<Long, InventoryBalance> balances = new HashMap<>();
        if (!items.isEmpty()) {
            inventoryBalanceRepository
                    .findAllByVariantIds(items.stream()
                            .map(item -> item.getVariant().getId())
                            .toList())
                    .forEach(balance -> balances.put(balance.getVariantId(), balance));
        }
        Instant now = now();
        List<CartItemResponse> responses = items.stream()
                .map(item -> {
                    CartAvailability availability =
                            availability(item, balances.get(item.getVariant().getId()));
                    item.setAvailability(availability.name());
                    item.setAvailable(availability == CartAvailability.AVAILABLE);
                    return cartItemMapper.fromEntityToResponse(item, now);
                })
                .toList();
        BigDecimal subtotal = responses.stream()
                .map(CartItemResponse::getEstimatedSubtotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        int totalQuantity = items.stream().mapToInt(CartItem::getQuantity).sum();
        return new CartResponse(responses, responses.size(), totalQuantity, subtotal, currency());
    }

    private CartResponse emptyCart() {
        return new CartResponse(List.of(), 0, 0, BigDecimal.ZERO.setScale(2), currency());
    }

    private CartAvailability availability(CartItem item, InventoryBalance balance) {
        if (!isSaleable(item.getVariant())) return CartAvailability.UNAVAILABLE;
        long available = balance == null ? 0 : balance.available();
        return available >= item.getQuantity() ? CartAvailability.AVAILABLE : CartAvailability.INSUFFICIENT_STOCK;
    }

    private ProductVariant getSaleableVariant(Long variantId) {
        return productVariantRepository
                .findEligibleStorefrontVariant(variantId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "variant", "resourceId", variantId)));
    }

    private void ensureAvailable(Long variantId, int desired) {
        InventoryBalance balance =
                inventoryBalanceRepository.findByVariantIdWithVariant(variantId).orElse(null);
        if (balance == null || balance.available() < desired) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "insufficientStock"));
        }
    }

    private static boolean isSaleable(ProductVariant variant) {
        if (variant == null || variant.getStatus() != ProductVariantStatus.ACTIVE) return false;
        var product = variant.getProduct();
        if (product == null
                || !product.isPublished()
                || product.getBrand() == null
                || product.getBrand().getStatus() != BrandStatus.ACTIVE
                || product.getCategory() == null
                || !product.getCategory().isEffectivelyActive()) return false;
        return variant.getSelections().stream()
                .allMatch(selection -> selection.getOptionValue() != null
                        && selection.getOptionValue().getStatus() == VariantOptionStatus.ACTIVE
                        && selection.getOptionValue().getOption() != null
                        && selection.getOptionValue().getOption().getStatus() == VariantOptionStatus.ACTIVE);
    }

    private static int safeSum(Integer current, Integer increment) {
        if (current == null) throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        try {
            return Math.addExact(current, increment);
        } catch (ArithmeticException exception) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, exception);
        }
    }

    private static void validateRequest(Integer quantity) {
        if (quantity == null || quantity < CartItem.MIN_QUANTITY || quantity > CartItem.MAX_QUANTITY) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "invalidQuantity"));
        }
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user")));
    }

    private Instant now() {
        return (pricingClock == null ? Clock.systemUTC() : pricingClock).instant();
    }

    private String currency() {
        return pricingProperties == null || pricingProperties.getCurrency() == null
                ? "VND"
                : pricingProperties.getCurrency();
    }
}
