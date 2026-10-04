package com.xdpsx.ecommerce.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.cart.persistence.CartRepository;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@SpringJUnitConfig(InventoryPersistenceTest.PersistenceConfig.class)
class CartConcurrencyTest {
    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            cartItemRepository.deleteAll();
            cartRepository.deleteAll();
            variantRepository.deleteAll();
            productRepository.deleteAll();
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    void cartItems_ShouldBeScopedToTheirCart() {
        Long firstCartId = seedCart("first", 1);
        Long secondCartId = seedCart("second", 2);

        assertThat(cartItemRepository.findByCartIdWithCatalog(firstCartId)).hasSize(1);
        assertThat(cartItemRepository.findByCartIdWithCatalog(secondCartId))
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(2);
    }

    @Test
    void concurrentCartLocks_ShouldSerializeQuantityUpdates() throws Exception {
        Long cartId = seedCart("concurrent", 1);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> increment(cartId, true, firstLocked, secondStarted, releaseFirst));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second =
                    executor.submit(() -> increment(cartId, false, firstLocked, secondStarted, releaseFirst));
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertThat(cartItemRepository.findByCartIdWithCatalog(cartId))
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(3);
    }

    private void increment(
            Long cartId,
            boolean holdLock,
            CountDownLatch firstLocked,
            CountDownLatch secondStarted,
            CountDownLatch releaseFirst) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!holdLock) secondStarted.countDown();
            cartRepository.findByIdForUpdate(cartId).orElseThrow();
            if (holdLock) {
                firstLocked.countDown();
                await(releaseFirst);
            }
            CartItem item = cartItemRepository
                    .findAllByCartIdOrderByCreatedAtAsc(cartId)
                    .getFirst();
            item.increaseBy(1);
            cartItemRepository.saveAndFlush(item);
        });
    }

    private Long seedCart(String suffix, int quantity) {
        return transactionTemplate.execute(status -> {
            User user = userRepository.saveAndFlush(User.builder()
                    .name("Customer " + suffix)
                    .email(suffix + "@example.test")
                    .role(Role.USER)
                    .authProvider(AuthProvider.LOCAL)
                    .build());
            Category category = categoryRepository.saveAndFlush(Category.builder()
                    .name("Category " + suffix)
                    .slug("category-" + suffix)
                    .status(CategoryStatus.ACTIVE)
                    .displayOrder(0)
                    .build());
            Brand brand = brandRepository.saveAndFlush(Brand.builder()
                    .name("Brand " + suffix)
                    .status(BrandStatus.ACTIVE)
                    .build());
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Product " + suffix)
                    .slug("product-" + suffix)
                    .category(category)
                    .brand(brand)
                    .published(true)
                    .build());
            ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("SKU-" + suffix)
                    .basePrice(BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey(suffix)
                    .build());
            Cart cart = cartRepository.saveAndFlush(Cart.forCustomer(user));
            cartItemRepository.saveAndFlush(CartItem.builder()
                    .id(new CartItemId(cart.getId(), variant.getId()))
                    .cart(cart)
                    .variant(variant)
                    .quantity(quantity)
                    .build());
            return cart.getId();
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while testing Cart locking", exception);
        }
    }
}
