package com.xdpsx.ecommerce.cart.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.xdpsx.ecommerce.config.CartProperties;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@Service
public class CartServiceImpl implements CartService {
    private final CartItemMapper cartItemMapper;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final StorePricingProperties pricingProperties;
    private final Clock pricingClock;
    private final GuestCartCredentialService guestCredentialService;
    private final CartProperties cartProperties;

    @Autowired
    public CartServiceImpl(
            CartItemMapper cartItemMapper,
            UserRepository userRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            InventoryBalanceRepository inventoryBalanceRepository,
            StorePricingProperties pricingProperties,
            Clock pricingClock,
            GuestCartCredentialService guestCredentialService,
            CartProperties cartProperties) {
        this.cartItemMapper = cartItemMapper;
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.inventoryBalanceRepository = inventoryBalanceRepository;
        this.pricingProperties = pricingProperties;
        this.pricingClock = pricingClock;
        this.guestCredentialService = guestCredentialService;
        this.cartProperties = cartProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(CartOwner owner) {
        if (owner.isCustomer()) {
            User user = getUser(owner.customerEmail());
            return readCart(cartRepository.findByUserId(user.getId()).orElse(null));
        }
        if (owner.guestCredential() == null) return emptyCart();
        GuestAccess guest = loadGuest(owner.guestCredential(), false);
        return readCart(guest.cart());
    }

    @Override
    @Transactional
    public CartMutationResult addItem(CartOwner owner, CartItemRequest request) {
        validateRequest(request.getQuantity());
        MutationAccess access = resolveForMutation(owner);
        Cart cart = access.cart();
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
        return access.result(readCart(cart));
    }

    @Override
    @Transactional
    public CartMutationResult replaceItem(CartOwner owner, Long variantId, CartQuantityRequest request) {
        validateRequest(request.getQuantity());
        MutationAccess access = resolveForMutation(owner);
        Cart cart = access.cart();
        CartItem item = getCartItem(cart, variantId);
        ProductVariant variant = getSaleableVariant(variantId);
        ensureAvailable(variant.getId(), request.getQuantity());
        item.replaceQuantity(request.getQuantity());
        return access.result(readCart(cart));
    }

    @Override
    @Transactional
    public CartMutationResult removeItem(CartOwner owner, Long variantId) {
        MutationAccess access = resolveForMutation(owner);
        Cart cart = access.cart();
        CartItem item = getCartItem(cart, variantId);
        cartItemRepository.delete(item);
        return access.result(readCart(cart));
    }

    @Override
    @Transactional
    public CartMutationResult claimGuestCart(String userEmail, String guestCredential) {
        User user = getUser(userEmail);
        if (guestCredential == null) {
            return new CartMutationResult(getCart(CartOwner.customer(userEmail)), null, null, true);
        }

        GuestCartCredentialService.ParsedCredential parsed = parseCredential(guestCredential);
        Optional<Cart> customerSnapshot = cartRepository.findByUserId(user.getId());
        if (customerSnapshot.isEmpty()) {
            User lockedUser = userRepository.findByIdForUpdate(user.getId()).orElseThrow();
            Optional<Cart> recheckedCustomer = cartRepository.findByUserIdForUpdate(lockedUser.getId());
            if (recheckedCustomer.isEmpty()) {
                Cart guest = loadGuestForUpdate(parsed);
                guest.claimFor(lockedUser);
                cartRepository.save(guest);
                return new CartMutationResult(readCart(guest), null, null, true);
            }
            customerSnapshot = recheckedCustomer;
        }

        Cart customer = customerSnapshot.orElseThrow();
        LockedCarts lockedCarts = lockGuestAndCustomer(parsed.cartId(), customer.getId());
        customer = lockedCarts.customer();
        Cart guest = lockedCarts.guest();
        validateGuest(guest, parsed);
        mergeGuestIntoCustomer(guest, customer);
        cartRepository.delete(guest);
        return new CartMutationResult(readCart(customer), null, null, true);
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

    private MutationAccess resolveForMutation(CartOwner owner) {
        if (owner.isCustomer()) return MutationAccess.customer(getOrCreateCustomerCart(owner.customerEmail()));
        if (owner.guestCredential() == null) return createGuestCart();
        GuestAccess guest = loadGuest(owner.guestCredential(), true);
        Instant expiresAt = now().plus(cartProperties.getGuestLifetime());
        guest.cart().renewGuestUntil(expiresAt);
        return MutationAccess.guest(guest.cart(), owner.guestCredential(), expiresAt);
    }

    private MutationAccess createGuestCart() {
        GuestCartCredentialService.IssuedSecret issued = guestCredentialService.issueSecret();
        Instant expiresAt = now().plus(cartProperties.getGuestLifetime());
        Cart cart = cartRepository.saveAndFlush(Cart.forGuest(issued.secretHash(), expiresAt));
        return MutationAccess.guest(cart, guestCredentialService.format(cart.getId(), issued.secret()), expiresAt);
    }

    private GuestAccess loadGuest(String rawCredential, boolean forUpdate) {
        GuestCartCredentialService.ParsedCredential parsed = parseCredential(rawCredential);
        Cart cart = (forUpdate
                        ? cartRepository.findByIdForUpdate(parsed.cartId())
                        : cartRepository.findById(parsed.cartId()))
                .orElseThrow(this::invalidGuestCart);
        validateGuest(cart, parsed);
        return new GuestAccess(cart);
    }

    private Cart loadGuestForUpdate(GuestCartCredentialService.ParsedCredential parsed) {
        Cart cart = cartRepository.findByIdForUpdate(parsed.cartId()).orElseThrow(this::invalidGuestCart);
        validateGuest(cart, parsed);
        return cart;
    }

    private LockedCarts lockGuestAndCustomer(Long guestId, Long customerId) {
        if (guestId.equals(customerId)) throw invalidGuestCart();
        if (guestId < customerId) {
            Cart guest = cartRepository.findByIdForUpdate(guestId).orElseThrow(this::invalidGuestCart);
            Cart customer = cartRepository.findByIdForUpdate(customerId).orElseThrow();
            return new LockedCarts(guest, customer);
        }
        Cart customer = cartRepository.findByIdForUpdate(customerId).orElseThrow();
        Cart guest = cartRepository.findByIdForUpdate(guestId).orElseThrow(this::invalidGuestCart);
        return new LockedCarts(guest, customer);
    }

    private record LockedCarts(Cart guest, Cart customer) {}

    private void validateGuest(Cart cart, GuestCartCredentialService.ParsedCredential parsed) {
        if (!cart.isGuestOwned()
                || cart.isExpiredAt(now())
                || !guestCredentialService.matches(cart.getGuestSecretHash(), parsed.secret())) {
            throw invalidGuestCart();
        }
    }

    private GuestCartCredentialService.ParsedCredential parseCredential(String rawCredential) {
        return guestCredentialService.parse(rawCredential).orElseThrow(this::invalidGuestCart);
    }

    private ApplicationException invalidGuestCart() {
        return new ApplicationException(ErrorCode.INVALID_GUEST_CART);
    }

    private void mergeGuestIntoCustomer(Cart guest, Cart customer) {
        Map<Long, CartItem> customerItems = new HashMap<>();
        cartItemRepository
                .findAllByCartIdOrderByCreatedAtAsc(customer.getId())
                .forEach(item -> customerItems.put(item.getVariant().getId(), item));
        for (CartItem guestItem : cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(guest.getId())) {
            CartItem customerItem = customerItems.get(guestItem.getVariant().getId());
            if (customerItem == null) {
                cartItemRepository.delete(guestItem);
                cartItemRepository.save(CartItem.builder()
                        .id(new CartItemId(
                                customer.getId(), guestItem.getVariant().getId()))
                        .cart(customer)
                        .variant(guestItem.getVariant())
                        .quantity(guestItem.getQuantity())
                        .build());
            } else {
                customerItem.replaceQuantity(
                        Math.min(CartItem.MAX_QUANTITY, safeSum(customerItem.getQuantity(), guestItem.getQuantity())));
                cartItemRepository.delete(guestItem);
            }
        }
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

    private record GuestAccess(Cart cart) {}

    private record MutationAccess(Cart cart, String guestCredential, Instant guestExpiresAt) {
        static MutationAccess customer(Cart cart) {
            return new MutationAccess(cart, null, null);
        }

        static MutationAccess guest(Cart cart, String credential, Instant expiresAt) {
            return new MutationAccess(cart, credential, expiresAt);
        }

        CartMutationResult result(CartResponse response) {
            return new CartMutationResult(response, guestCredential, guestExpiresAt, false);
        }
    }
}
