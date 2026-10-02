package com.xdpsx.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.CartProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class GuestCartServiceImplTest {
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

    private GuestCartCredentialService credentialService;
    private CartProperties properties;
    private User user;
    private ProductVariant variant;
    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        credentialService = new GuestCartCredentialService();
        properties = new CartProperties();
        properties.setGuestLifetime(Duration.ofDays(30));
        user = User.builder().id(7L).email("buyer@example.test").build();
        variant = ProductVariant.builder().id(101L).build();
        service = new CartServiceImpl(
                mapper,
                userRepository,
                cartRepository,
                cartItemRepository,
                variantRepository,
                inventoryRepository,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC),
                credentialService,
                properties);
    }

    @Test
    void addItemWithoutCookie_ShouldCreateGuestCartAndIssueScopedCredential() {
        Cart saved = Cart.builder().id(42L).build();
        when(cartRepository.saveAndFlush(any(Cart.class))).thenReturn(saved);
        when(variantRepository.findEligibleStorefrontVariant(101L)).thenReturn(Optional.of(variant));
        when(inventoryRepository.findByVariantIdWithVariant(101L)).thenReturn(Optional.of(balance));
        when(balance.available()).thenReturn(5L);
        when(cartItemRepository.findByCartIdAndVariantId(42L, 101L)).thenReturn(Optional.empty());
        when(cartItemRepository.findByCartIdWithCatalog(42L)).thenReturn(List.of());

        CartMutationResult result = service.addItem(CartOwner.guest(null), request(2));

        assertThat(result.guestCredential()).startsWith("42.");
        assertThat(result.guestExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(result.expireGuestCookie()).isFalse();
        assertThat(credentialService.parse(result.guestCredential())).isPresent();
        verify(cartRepository).saveAndFlush(any(Cart.class));
    }

    @Test
    void readWithExpiredOrForgedCookie_ShouldReturnOneGenericFailureWithoutMutation() {
        GuestCartCredentialService.IssuedSecret issued = credentialService.issueSecret();
        Cart expired = guest(42L, issued.secretHash(), NOW.minusSeconds(1));
        String credential = credentialService.format(42L, issued.secret());
        when(cartRepository.findById(42L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.getCart(CartOwner.guest(credential)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                        .isEqualTo(com.xdpsx.ecommerce.common.error.ErrorCode.INVALID_GUEST_CART));

        GuestCartCredentialService.IssuedSecret forgedSecret = credentialService.issueSecret();
        String forged = credentialService.format(42L, forgedSecret.secret());
        assertThatThrownBy(() -> service.getCart(CartOwner.guest(forged)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> assertThat(((ApplicationException) error).getCode())
                        .isEqualTo(com.xdpsx.ecommerce.common.error.ErrorCode.INVALID_GUEST_CART));
        verify(cartRepository, never()).save(any());
    }

    @Test
    void claim_ShouldCapDuplicateQuantityAndExpireCredential() {
        GuestCartCredentialService.IssuedSecret issued = credentialService.issueSecret();
        Cart guest = guest(10L, issued.secretHash(), NOW.plus(Duration.ofDays(1)));
        Cart customer = Cart.builder().id(20L).user(user).build();
        CartItem guestItem = item(guest, 101L, 10);
        CartItem customerItem = item(customer, 101L, 95);
        String credential = credentialService.format(10L, issued.secret());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.of(customer));
        when(cartRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(guest));
        when(cartRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(customer));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(customerItem));
        when(cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(guestItem));
        when(cartItemRepository.findByCartIdWithCatalog(20L)).thenReturn(List.of());

        CartMutationResult result = service.claimGuestCart(user.getEmail(), credential);

        assertThat(customerItem.getQuantity()).isEqualTo(99);
        assertThat(result.expireGuestCookie()).isTrue();
        assertThat(result.guestCredential()).isNull();
        verify(cartItemRepository).delete(guestItem);
        verify(cartRepository).delete(guest);
    }

    @Test
    void claimWithoutCustomerCart_ShouldConvertGuestCartInsteadOfCopyingIt() {
        GuestCartCredentialService.IssuedSecret issued = credentialService.issueSecret();
        Cart guest = guest(10L, issued.secretHash(), NOW.plus(Duration.ofDays(1)));
        String credential = credentialService.format(10L, issued.secret());

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(cartRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.empty());
        when(cartRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(guest));
        when(cartItemRepository.findByCartIdWithCatalog(10L)).thenReturn(List.of());

        CartMutationResult result = service.claimGuestCart(user.getEmail(), credential);

        assertThat(guest.isCustomerOwned()).isTrue();
        assertThat(guest.getUser()).isEqualTo(user);
        assertThat(result.expireGuestCookie()).isTrue();
        verify(cartRepository).save(guest);
        verify(cartItemRepository, never()).save(any());
    }

    private static CartItemRequest request(int quantity) {
        CartItemRequest request = new CartItemRequest();
        request.setVariantId(101L);
        request.setQuantity(quantity);
        return request;
    }

    private static CartItem item(Cart cart, Long variantId, int quantity) {
        return CartItem.builder()
                .id(new CartItemId(cart.getId(), variantId))
                .cart(cart)
                .variant(ProductVariant.builder().id(variantId).build())
                .quantity(quantity)
                .build();
    }

    private static Cart guest(Long id, byte[] secretHash, Instant expiresAt) {
        return Cart.builder()
                .id(id)
                .guestSecretHash(secretHash)
                .guestExpiresAt(expiresAt)
                .build();
    }
}
