package com.xdpsx.ecommerce.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {
    @Mock
    private CartItemMapper cartItemMapper;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository productVariantRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    @Test
    void getCart_ShouldMarkOnlyEligibleAndInStockVariantsAvailable() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Product product = Product.builder().id(11L).name("Shirt").slug("shirt").build();
        ProductVariant available = ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-BLACK")
                .build();
        ProductVariant soldOut = ProductVariant.builder()
                .id(102L)
                .product(product)
                .sku("SHIRT-WHITE")
                .build();
        CartItem availableItem = item(user, available);
        CartItem soldOutItem = item(user, soldOut);
        CartItemResponse availableResponse = new CartItemResponse();
        CartItemResponse soldOutResponse = new CartItemResponse();

        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartItemRepository.findNewestByUserId(user.getId())).thenReturn(List.of(availableItem, soldOutItem));
        when(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()))
                .thenReturn(List.of(101L));
        when(cartItemMapper.fromEntityToResponse(eq(availableItem), any(Instant.class)))
                .thenReturn(availableResponse);
        when(cartItemMapper.fromEntityToResponse(eq(soldOutItem), any(Instant.class)))
                .thenReturn(soldOutResponse);

        assertThat(cartService.getCart(user.getEmail())).containsExactly(availableResponse, soldOutResponse);
        assertThat(availableItem.isAvailable()).isTrue();
        assertThat(soldOutItem.isAvailable()).isFalse();
        verify(cartItemRepository, times(1)).findNewestByUserId(user.getId());
        verify(cartItemRepository, times(1)).findAvailableEligibleVariantIdsByUserId(user.getId());
    }

    private static CartItem item(User user, ProductVariant variant) {
        return CartItem.builder()
                .id(new CartItemId(user.getId(), variant.getId()))
                .user(user)
                .variant(variant)
                .quantity(1)
                .build();
    }
}
