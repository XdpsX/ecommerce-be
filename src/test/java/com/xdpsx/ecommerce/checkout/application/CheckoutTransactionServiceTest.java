package com.xdpsx.ecommerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.CheckoutProperties;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.domain.UserAddress;
import com.xdpsx.ecommerce.user.persistence.UserAddressRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class CheckoutTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAddressRepository userAddressRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Mock
    private OrderRepository orderRepository;

    private CheckoutTransactionService service;
    private CheckoutProperties properties;
    private StorePricingProperties pricing;

    @BeforeEach
    void setUp() {
        properties = new CheckoutProperties();
        pricing = new StorePricingProperties();
        service = new CheckoutTransactionService(
                userRepository,
                userAddressRepository,
                cartRepository,
                cartItemRepository,
                productRepository,
                productVariantRepository,
                inventoryBalanceRepository,
                orderRepository,
                properties,
                pricing,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void execute_ShouldSnapshotAddressReserveInventoryAndClearCart() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        ProductVariant variant = saleableVariant();
        Cart cart = Cart.builder().id(3L).user(user).build();
        CartItem item = CartItem.builder()
                .id(new CartItemId(3L, 101L))
                .cart(cart)
                .variant(variant)
                .quantity(2)
                .build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(5);
        UserAddress address = org.mockito.Mockito.mock(UserAddress.class);
        when(address.getRecipientName()).thenReturn("Buyer");
        when(address.getPhoneNumber()).thenReturn("0123456789");
        when(address.getAddressLine()).thenReturn("1 Main Street");
        when(address.getWardCommune()).thenReturn("Ward 1");
        when(address.getDistrict()).thenReturn("District 1");
        when(address.getProvinceCity()).thenReturn("HCMC");
        when(address.getPostalCode()).thenReturn("700000");
        arrange(user, address, cart, item, variant, balance);
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(order, "id", 42L);
            return order;
        });

        CheckoutTransactionResult result = service.execute("buyer@example.test", request(10L, " office "), " key-1 ");

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.order().getTotalAmount()).isEqualByComparingTo("25.00");
        assertThat(result.order().getShippingAddress().getAddressLine()).isEqualTo("1 Main Street");
        assertThat(result.order().getReservationExpiresAt()).isEqualTo(NOW.plus(properties.getReservationLifetime()));
        assertThat(balance.getReserved()).isEqualTo(2);
        verify(cartItemRepository).deleteAllByCartId(3L);
    }

    @Test
    void execute_ShouldReplayBeforeReadingAddressOrCart() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        CheckoutRequest request = request(10L, "office");
        Order existing = Order.builder()
                .id(42L)
                .user(user)
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        existing.setCheckoutRequestHash(CheckoutIdempotency.requestHash(request));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdAndIdempotencyKeyHash(user.getId(), CheckoutIdempotency.keyHash("key-1")))
                .thenReturn(Optional.of(existing));

        CheckoutTransactionResult result = service.execute(user.getEmail(), request, "key-1");

        assertThat(result.order()).isSameAs(existing);
        assertThat(result.replayed()).isTrue();
        verifyNoInteractions(userAddressRepository, cartRepository, cartItemRepository, inventoryBalanceRepository);
    }

    @Test
    void execute_ShouldRollbackReservationWhenStockIsInsufficient() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        ProductVariant variant = saleableVariant();
        Cart cart = Cart.builder().id(3L).user(user).build();
        CartItem item = CartItem.builder()
                .id(new CartItemId(3L, 101L))
                .cart(cart)
                .variant(variant)
                .quantity(2)
                .build();
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(1);
        UserAddress address = org.mockito.Mockito.mock(UserAddress.class);
        when(address.getRecipientName()).thenReturn("Buyer");
        when(address.getPhoneNumber()).thenReturn("0123456789");
        when(address.getAddressLine()).thenReturn("1 Main Street");
        when(address.getWardCommune()).thenReturn("Ward 1");
        when(address.getDistrict()).thenReturn("District 1");
        when(address.getProvinceCity()).thenReturn("HCMC");
        arrange(user, address, cart, item, variant, balance);

        assertThatThrownBy(() -> service.execute(user.getEmail(), request(10L, null), "key-1"))
                .isInstanceOf(ApplicationException.class);
        assertThat(balance.getReserved()).isZero();
        verify(orderRepository, never()).saveAndFlush(any());
        verify(cartItemRepository, never()).deleteAllByCartId(any());
    }

    private void arrange(
            User user,
            UserAddress address,
            Cart cart,
            CartItem item,
            ProductVariant variant,
            InventoryBalance balance) {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(orderRepository.findByUserIdAndIdempotencyKeyHash(any(), any())).thenReturn(Optional.empty());
        when(userAddressRepository.findByIdAndUserId(10L, user.getId())).thenReturn(Optional.of(address));
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(cart.getId()))
                .thenReturn(List.of(item));
        when(productVariantRepository.findProductIdsByIds(List.of(101L))).thenReturn(List.of(11L));
        when(productRepository.findAllByIdForUpdate(List.of(11L))).thenReturn(List.of(variant.getProduct()));
        when(productVariantRepository.findAllByIdForUpdate(List.of(101L))).thenReturn(List.of(variant));
        when(inventoryBalanceRepository.findAllByVariantIdsForUpdate(List.of(101L)))
                .thenReturn(List.of(balance));
    }

    private static CheckoutRequest request(long addressId, String description) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddressId(addressId);
        request.setDescription(description);
        return request;
    }

    private static ProductVariant saleableVariant() {
        Category category = Category.builder()
                .id(1)
                .name("Clothes")
                .slug("clothes")
                .status(CategoryStatus.ACTIVE)
                .build();
        Brand brand =
                Brand.builder().id(1).name("Brand").status(BrandStatus.ACTIVE).build();
        Product product = Product.builder()
                .id(11L)
                .name("Shirt")
                .slug("shirt")
                .published(true)
                .category(category)
                .brand(brand)
                .build();
        return ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-1")
                .combinationKey("default")
                .status(ProductVariantStatus.ACTIVE)
                .basePrice(new BigDecimal("12.50"))
                .build();
    }
}
