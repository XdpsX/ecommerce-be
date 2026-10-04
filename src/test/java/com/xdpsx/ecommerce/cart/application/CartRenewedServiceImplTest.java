package com.xdpsx.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.cart.api.dto.CartAvailability;
import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.CartProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class CartRenewedServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private CartItemMapper mapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private InventoryBalanceRepository inventoryRepository;

    @Mock
    private InventoryBalance balance;

    private User user;
    private Cart cart;
    private ProductVariant variant;
    private CartItem item;
    private CartServiceImpl service;
    private GuestCartCredentialService guestCredentialService;
    private CartProperties cartProperties;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).email("buyer@example.test").build();
        cart = Cart.builder().id(12L).user(user).build();
        variant = ProductVariant.builder().id(101L).build();
        item = CartItem.builder().cart(cart).variant(variant).quantity(2).build();
        guestCredentialService = new GuestCartCredentialService();
        cartProperties = new CartProperties();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        service = new CartServiceImpl(
                mapper,
                userRepository,
                cartRepository,
                cartItemRepository,
                variantRepository,
                inventoryRepository,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                guestCredentialService,
                cartProperties);
    }

    @Test
    void addItem_ShouldIncrementAnExistingSku() {
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(variantRepository.findEligibleStorefrontVariant(variant.getId())).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId()))
                .thenReturn(Optional.of(item));
        when(inventoryRepository.findByVariantIdWithVariant(variant.getId())).thenReturn(Optional.of(balance));
        when(balance.available()).thenReturn(10L);
        when(inventoryRepository.findAllByVariantIds(List.of(variant.getId()))).thenReturn(List.of(balance));
        when(balance.getVariantId()).thenReturn(variant.getId());
        when(cartItemRepository.findByCartIdWithCatalog(cart.getId())).thenReturn(List.of(item));
        when(mapper.fromEntityToResponse(any(CartItem.class), any(Instant.class)))
                .thenReturn(itemResponse());

        CartItemRequest request = request(3);

        service.addItem(CartOwner.customer(user.getEmail()), request);

        assertThat(item.getQuantity()).isEqualTo(5);
    }

    @Test
    void addItem_ShouldRejectInsufficientStockWithoutChangingTheItem() {
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(cart));
        when(variantRepository.findEligibleStorefrontVariant(variant.getId())).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId()))
                .thenReturn(Optional.of(item));
        when(inventoryRepository.findByVariantIdWithVariant(variant.getId())).thenReturn(Optional.of(balance));
        when(balance.available()).thenReturn(3L);

        assertThatThrownBy(() -> service.addItem(CartOwner.customer(user.getEmail()), request(2)))
                .isInstanceOf(ApplicationException.class);

        assertThat(item.getQuantity()).isEqualTo(2);
    }

    @Test
    void addItem_WhenCustomerCartIsAbsent_ShouldLockTheUserBeforeCreatingIt() {
        Cart created = Cart.builder().id(12L).user(user).build();
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(cartRepository.save(any(Cart.class))).thenReturn(created);
        when(variantRepository.findEligibleStorefrontVariant(variant.getId())).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCartIdAndVariantId(created.getId(), variant.getId()))
                .thenReturn(Optional.empty());
        when(inventoryRepository.findByVariantIdWithVariant(variant.getId())).thenReturn(Optional.of(balance));
        when(balance.available()).thenReturn(5L);
        when(cartItemRepository.findByCartIdWithCatalog(created.getId())).thenReturn(List.of());

        service.addItem(CartOwner.customer(user.getEmail()), request(1));

        InOrder order = inOrder(cartRepository, userRepository);
        order.verify(cartRepository).findByUserIdForUpdate(user.getId());
        order.verify(userRepository).findByIdForUpdate(user.getId());
        order.verify(cartRepository).findByUserIdForUpdate(user.getId());
        order.verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void readCart_ShouldRetainTheItemAndReportCurrentAvailabilityState() {
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(cart));
        when(inventoryRepository.findAllByVariantIds(List.of(variant.getId()))).thenReturn(List.of(balance));
        when(balance.getVariantId()).thenReturn(variant.getId());
        when(cartItemRepository.findByCartIdWithCatalog(cart.getId())).thenReturn(List.of(item));
        Brand brand = Brand.builder().status(BrandStatus.ACTIVE).build();
        Category category =
                Category.builder().status(CategoryStatus.ACTIVE).displayOrder(0).build();
        Product product = Product.builder()
                .id(11L)
                .name("Shirt")
                .slug("shirt")
                .published(true)
                .brand(brand)
                .category(category)
                .build();
        variant.setProduct(product);
        variant.setStatus(ProductVariantStatus.ACTIVE);
        variant.setBasePrice(new BigDecimal("10.00"));
        variant.setCombinationKey("shirt");

        service = new CartServiceImpl(
                new CartItemMapper() {},
                userRepository,
                cartRepository,
                cartItemRepository,
                variantRepository,
                inventoryRepository,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                guestCredentialService,
                cartProperties);
        when(balance.available()).thenReturn(0L);

        assertThat(service.getCart(CartOwner.customer(user.getEmail())).getItems())
                .singleElement()
                .extracting(CartItemResponse::getAvailability)
                .isEqualTo(CartAvailability.INSUFFICIENT_STOCK);

        variant.setStatus(ProductVariantStatus.INACTIVE);

        assertThat(service.getCart(CartOwner.customer(user.getEmail())).getItems())
                .singleElement()
                .extracting(CartItemResponse::getAvailability)
                .isEqualTo(CartAvailability.UNAVAILABLE);
    }

    private static CartItemRequest request(int quantity) {
        CartItemRequest request = new CartItemRequest();
        request.setVariantId(101L);
        request.setQuantity(quantity);
        return request;
    }

    private static CartItemResponse itemResponse() {
        CartItemResponse response = new CartItemResponse();
        response.setVariantId(101L);
        response.setQuantity(2);
        return response;
    }
}
