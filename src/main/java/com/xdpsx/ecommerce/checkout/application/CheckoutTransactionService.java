package com.xdpsx.ecommerce.checkout.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.CheckoutProperties;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderItem;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.domain.ShippingAddressSnapshot;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserAddressRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutTransactionService {
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final OrderRepository orderRepository;
    private final CheckoutProperties checkoutProperties;
    private final StorePricingProperties pricingProperties;
    private final Clock clock;

    @Transactional
    public CheckoutTransactionResult execute(String userEmail, CheckoutRequest request, String rawIdempotencyKey) {
        String idempotencyKey = CheckoutIdempotency.normalizeKey(rawIdempotencyKey);
        byte[] keyHash = CheckoutIdempotency.keyHash(idempotencyKey);
        byte[] requestHash = CheckoutIdempotency.requestHash(request);

        User canonicalUser = userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(
                        () -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user")));
        User user = userRepository.findByIdForUpdate(canonicalUser.getId()).orElseThrow();

        var existing = orderRepository.findByUserIdAndIdempotencyKeyHash(user.getId(), keyHash);
        if (existing.isPresent()) {
            if (!CheckoutIdempotency.same(existing.get().getCheckoutRequestHash(), requestHash)) {
                throw new ApplicationException(ErrorCode.RESOURCE_ALREADY_EXISTS, Map.of("field", "Idempotency-Key"));
            }
            return new CheckoutTransactionResult(existing.get(), true);
        }

        var address = userAddressRepository
                .findByIdAndUserId(request.getAddressId(), user.getId())
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("resourceType", "userAddress", "resourceId", request.getAddressId())));
        ShippingAddressSnapshot shipping = ShippingAddressSnapshot.from(address);
        shipping.validateComplete();

        Cart cart = cartRepository.findByUserIdForUpdate(user.getId()).orElseGet(() -> {
            return cartRepository.saveAndFlush(Cart.forCustomer(user));
        });
        List<CartItem> cartItems = cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(cart.getId());
        if (cartItems.isEmpty()) throw new ApplicationException(ErrorCode.CART_EMPTY);

        List<Long> variantIds = cartItems.stream()
                .map(item -> item.getId().getVariantId())
                .sorted()
                .toList();
        List<Long> productIds = productVariantRepository.findProductIdsByIds(variantIds).stream()
                .distinct()
                .sorted()
                .toList();
        if (productIds.isEmpty()
                || productRepository.findAllByIdForUpdate(productIds).size() != productIds.size()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "unavailableVariant"));
        }
        List<ProductVariant> lockedVariants = productVariantRepository.findAllByIdForUpdate(variantIds);
        if (lockedVariants.size() != Set.copyOf(variantIds).size()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "unavailableVariant"));
        }
        Map<Long, ProductVariant> lockedVariantsById = new HashMap<>();
        lockedVariants.forEach(variant -> lockedVariantsById.put(variant.getId(), variant));
        if (cartItems.stream()
                .anyMatch(
                        item -> !isSaleable(lockedVariantsById.get(item.getId().getVariantId())))) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "unavailableVariant"));
        }
        Map<Long, InventoryBalance> balances =
                inventoryBalanceRepository.findAllByVariantIdsForUpdate(variantIds).stream()
                        .collect(Collectors.toMap(InventoryBalance::getVariantId, value -> value));

        Instant now = clock.instant();
        String currency = pricingProperties.getCurrency();
        Order order = Order.builder()
                .trackingNumber(UUID.randomUUID().toString())
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .description(request.getDescription())
                .currency(currency)
                .idempotencyKeyHash(keyHash)
                .checkoutRequestHash(requestHash)
                .reservationExpiresAt(now.plus(checkoutProperties.getReservationLifetime()))
                .build();
        order.setShippingAddress(shipping);

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            ProductVariant variant = lockedVariantsById.get(item.getId().getVariantId());
            InventoryBalance balance = balances.get(variant.getId());
            if (balance == null) {
                throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "insufficientStock"));
            }
            if (balance.available() < item.getQuantity()) {
                throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "insufficientStock"));
            }
            balance.reserve(item.getQuantity());
            var resolved = variant.resolvePriceAt(now);
            BigDecimal subtotal = resolved.finalUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2);
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(variant.getProduct().getId())
                    .productName(variant.getProduct().getName())
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .variantDescription(variant.getSelections().stream()
                            .sorted(Comparator.comparing(s -> s.getOptionValue().getId()))
                            .map(s -> s.getOptionValue().getOption().getName() + ": "
                                    + s.getOptionValue().getName())
                            .collect(Collectors.joining(", ")))
                    .quantity(item.getQuantity())
                    .unitBasePrice(resolved.basePrice())
                    .discountAmount(resolved.discountAmount())
                    .finalUnitPrice(resolved.finalUnitPrice())
                    .subtotal(subtotal)
                    .currency(currency)
                    .build();
            order.getItems().add(orderItem);
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);
        order.setPayment(
                Payment.builder().order(order).status(PaymentStatus.UNPAID).build());
        Order saved = orderRepository.saveAndFlush(order);
        cartItemRepository.deleteAllByCartId(cart.getId());
        return new CheckoutTransactionResult(saved, false);
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
}
