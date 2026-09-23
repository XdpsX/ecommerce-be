package com.xdpsx.ecommerce.cart.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.cart.api.dto.CartItemRequest;
import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.common.error.NotFoundException;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {
    private final CartItemMapper cartItemMapper;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    public CartItemResponse addToCart(String userEmail, CartItemRequest request) {
        User user = getUser(userEmail);
        Product product = getProduct(request);
        CartItemId cartItemId = new CartItemId(user.getId(), product.getId());
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElse(null);
        if (cartItem == null) {
            CartItem newCartItem = CartItem.builder()
                    .id(cartItemId)
                    .quantity(request.getQuantity())
                    .user(user)
                    .product(product)
                    .build();
            return cartItemMapper.fromEntityToResponse(cartItemRepository.save(newCartItem));
        } else {
            cartItem.setQuantity(cartItem.getQuantity() + request.getQuantity());
            return cartItemMapper.fromEntityToResponse(cartItemRepository.save(cartItem));
        }
    }

    @Override
    public void removeCartItem(String userEmail, Long productId) {
        User user = getUser(userEmail);
        CartItemId cartItemId =
                CartItemId.builder().productId(productId).userId(user.getId()).build();
        CartItem cartItem = getCartItem(cartItemId);
        cartItemRepository.delete(cartItem);
    }

    private CartItem getCartItem(CartItemId cartItemId) {
        return cartItemRepository
                .findById(cartItemId)
                .orElseThrow(() -> new NotFoundException("Can not found Cart item"));
    }

    @Override
    public List<CartItemResponse> getCart(String userEmail) {
        User user = getUser(userEmail);
        List<CartItem> cartItems = cartItemRepository.findNewestByUserId(user.getId());
        return cartItems.stream().map(cartItemMapper::fromEntityToResponse).toList();
    }

    @Override
    public CartItemResponse updateCartItem(String userEmail, CartItemRequest request) {
        User user = getUser(userEmail);
        CartItemId cartItemId = new CartItemId(user.getId(), request.getProductId());
        CartItem cartItem = getCartItem(cartItemId);
        cartItem.setQuantity(request.getQuantity());
        return cartItemMapper.fromEntityToResponse(cartItemRepository.save(cartItem));
    }

    @Override
    public long countCartItems(String userEmail) {
        User user = getUser(userEmail);
        return cartItemRepository.countByUserId(user.getId());
    }

    private Product getProduct(CartItemRequest request) {
        return productRepository
                .findProductById(request.getProductId())
                .orElseThrow(
                        () -> new NotFoundException("Product with id=%s not found!".formatted(request.getProductId())));
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(userEmail)
                .orElseThrow(() -> new NotFoundException("User with email=%s not found".formatted(userEmail)));
    }
}
