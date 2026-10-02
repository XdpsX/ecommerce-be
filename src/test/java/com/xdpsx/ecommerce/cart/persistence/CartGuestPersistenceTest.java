package com.xdpsx.ecommerce.cart.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.cart.api.dto.CartItemResponse;
import com.xdpsx.ecommerce.cart.application.CartItemMapper;
import com.xdpsx.ecommerce.cart.application.CartOwner;
import com.xdpsx.ecommerce.cart.application.CartService;
import com.xdpsx.ecommerce.cart.application.ExpiredGuestCartCleanup;
import com.xdpsx.ecommerce.cart.application.GuestCartCredentialService;
import com.xdpsx.ecommerce.cart.domain.Cart;
import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductVariantRepository;
import com.xdpsx.ecommerce.config.CartProperties;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@SpringJUnitConfig(CartGuestPersistenceTest.PersistenceConfig.class)
class CartGuestPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                CartRepository.class,
                CartItemRepository.class,
                UserRepository.class,
                ProductRepository.class,
                ProductVariantRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:cart_guest;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.cart.domain",
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
                    "com.xdpsx.ecommerce.catalog.product.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.inventory.domain",
                    "com.xdpsx.ecommerce.media.domain",
                    "com.xdpsx.ecommerce.user.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        CartProperties cartProperties() {
            CartProperties properties = new CartProperties();
            properties.setCleanupBatchSize(2);
            properties.setGuestLifetime(Duration.ofDays(30));
            return properties;
        }

        @Bean
        GuestCartCredentialService guestCartCredentialService() {
            return new GuestCartCredentialService();
        }

        @Bean
        StorePricingProperties storePricingProperties() {
            return new StorePricingProperties();
        }

        @Bean
        CartItemMapper cartItemMapper() {
            CartItemMapper mapper = mock(CartItemMapper.class);
            org.mockito.Mockito.when(mapper.fromEntityToResponse(
                            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new CartItemResponse());
            return mapper;
        }

        @Bean
        InventoryBalanceRepository inventoryBalanceRepository() {
            return mock(InventoryBalanceRepository.class);
        }

        @Bean
        CartService cartService(
                CartItemMapper mapper,
                UserRepository userRepository,
                CartRepository cartRepository,
                CartItemRepository cartItemRepository,
                ProductVariantRepository productVariantRepository,
                InventoryBalanceRepository inventoryBalanceRepository,
                Clock clock,
                GuestCartCredentialService credentials,
                CartProperties properties) {
            return new com.xdpsx.ecommerce.cart.application.CartServiceImpl(
                    mapper,
                    userRepository,
                    cartRepository,
                    cartItemRepository,
                    productVariantRepository,
                    inventoryBalanceRepository,
                    null,
                    clock,
                    credentials,
                    properties);
        }

        @Bean
        ExpiredGuestCartCleanup expiredGuestCartCleanup(
                CartRepository cartRepository, CartProperties properties, Clock clock) {
            return new ExpiredGuestCartCleanup(cartRepository, properties, clock);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private CartRepository cartRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private CartItemRepository cartItemRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProductRepository productRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProductVariantRepository productVariantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private TransactionTemplate transactionTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    private CartService cartService;

    @org.springframework.beans.factory.annotation.Autowired
    private ExpiredGuestCartCleanup cleanup;

    @org.springframework.beans.factory.annotation.Autowired
    private GuestCartCredentialService credentials;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            cartItemRepository.deleteAll();
            cartRepository.deleteAll();
            productVariantRepository.deleteAll();
            productRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    void guestOwnershipAndExpiry_ShouldPersistAndRejectCrossCredentialAccess() {
        GuestCartCredentialService.IssuedSecret first = credentials.issueSecret();
        GuestCartCredentialService.IssuedSecret second = credentials.issueSecret();
        Cart firstCart = saveGuest(first, NOW.plus(Duration.ofDays(1)));
        Cart secondCart = saveGuest(second, NOW.plus(Duration.ofDays(1)));

        assertThat(cartService
                        .getCart(CartOwner.guest(credentials.format(firstCart.getId(), first.secret())))
                        .getItems())
                .isEmpty();
        assertThatThrownBy(() ->
                        cartService.getCart(CartOwner.guest(credentials.format(secondCart.getId(), first.secret()))))
                .isInstanceOf(com.xdpsx.ecommerce.common.error.ApplicationException.class);

        Instant renewed = NOW.plus(Duration.ofDays(30));
        transactionTemplate.executeWithoutResult(status -> {
            Cart managed = cartRepository.findById(firstCart.getId()).orElseThrow();
            managed.renewGuestUntil(renewed);
        });

        assertThat(cartRepository.findById(firstCart.getId()).orElseThrow().getGuestExpiresAt())
                .isEqualTo(renewed);
    }

    @Test
    void cleanup_ShouldBatchAndRecheckOwnershipAndExpiryBeforeDelete() {
        GuestCartCredentialService.IssuedSecret first = credentials.issueSecret();
        GuestCartCredentialService.IssuedSecret second = credentials.issueSecret();
        GuestCartCredentialService.IssuedSecret third = credentials.issueSecret();
        Cart renewed = saveGuest(first, NOW.minusSeconds(1));
        Cart deletedOne = saveGuest(second, NOW.minusSeconds(2));
        Cart deletedTwo = saveGuest(third, NOW.minusSeconds(3));

        transactionTemplate.executeWithoutResult(status -> {
            Cart managed = cartRepository.findById(renewed.getId()).orElseThrow();
            managed.renewGuestUntil(NOW.plusSeconds(1));
            assertThat(cartRepository.deleteExpiredGuestsIfStillExpired(List.of(renewed.getId()), NOW))
                    .isZero();
        });

        assertThat(cleanup.deleteExpiredGuestsNow()).isEqualTo(2);
        assertThat(cartRepository.findById(renewed.getId())).isPresent();
        assertThat(cartRepository.findById(deletedOne.getId())).isEmpty();
        assertThat(cartRepository.findById(deletedTwo.getId())).isEmpty();
    }

    @Test
    void concurrentClaim_ShouldLeaveOneCustomerCartAndOneItem() throws Exception {
        User user = transactionTemplate.execute(status -> userRepository.saveAndFlush(User.builder()
                .name("Buyer")
                .email("buyer@example.test")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build()));
        ProductVariant variant = transactionTemplate.execute(status -> {
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Claim product")
                    .slug("claim-product")
                    .build());
            return productVariantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("CLAIM-SKU")
                    .basePrice(java.math.BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("")
                    .build());
        });
        GuestCartCredentialService.IssuedSecret issued = credentials.issueSecret();
        Cart guest = transactionTemplate.execute(status -> {
            Cart saved = cartRepository.saveAndFlush(Cart.forGuest(issued.secretHash(), NOW.plus(Duration.ofDays(1))));
            cartItemRepository.saveAndFlush(CartItem.builder()
                    .id(new CartItemId(saved.getId(), variant.getId()))
                    .cart(saved)
                    .variant(variant)
                    .quantity(7)
                    .build());
            return saved;
        });
        String credential = credentials.format(guest.getId(), issued.secret());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> claimSuccessfully(user.getEmail(), credential, ready, start));
            Future<Boolean> second =
                    executor.submit(() -> claimSuccessfully(user.getEmail(), credential, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        Cart customerCart = cartRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(cartItemRepository.findAllByCartIdOrderByCreatedAtAsc(customerCart.getId()))
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(7);
        assertThat(cartRepository.findById(guest.getId())).isPresent();
        assertThat(cartRepository.findById(guest.getId()).orElseThrow().isCustomerOwned())
                .isTrue();
    }

    private boolean claimSuccessfully(String email, String credential, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            cartService.claimGuestCart(email, credential);
            return true;
        } catch (com.xdpsx.ecommerce.common.error.ApplicationException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private Cart saveGuest(GuestCartCredentialService.IssuedSecret issued, Instant expiresAt) {
        return transactionTemplate.execute(
                status -> cartRepository.saveAndFlush(Cart.forGuest(issued.secretHash(), expiresAt)));
    }
}
