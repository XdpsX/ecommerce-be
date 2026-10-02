package com.xdpsx.ecommerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

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
import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.CheckoutProperties;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.testsupport.MySqlTestContainerFactory;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.domain.UserAddress;
import com.xdpsx.ecommerce.user.persistence.UserAddressRepository;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(CheckoutConcurrencyTest.PersistenceConfig.class)
class CheckoutConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Container
    private static final MySQLContainer MYSQL = MySqlTestContainerFactory.create("checkout_concurrency");

    @org.springframework.beans.factory.annotation.Autowired
    private CheckoutTransactionService checkoutService;

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private UserAddressRepository addressRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private CartRepository cartRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private CartItemRepository cartItemRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProductRepository productRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private ProductVariantRepository variantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private InventoryBalanceRepository inventoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private PaymentRepository paymentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private CategoryRepository categoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private BrandRepository brandRepository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void insufficientSecondLine_ShouldRollbackEarlierReservationAndKeepCart() {
        ProductVariant first = saveVariant("rollback-first", 5);
        ProductVariant second = saveVariant("rollback-second", 0);
        User user = saveCustomer("rollback");
        UserAddress address = saveAddress(user);
        Cart cart = saveCart(user);
        saveItem(cart, first, 1);
        saveItem(cart, second, 1);

        Outcome outcome = invoke(user, address, "rollback-key");

        assertThat(outcome.error()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
        assertThat(inventoryRepository
                        .findByVariantIdWithVariant(first.getId())
                        .orElseThrow()
                        .getReserved())
                .isZero();
        assertThat(inventoryRepository
                        .findByVariantIdWithVariant(second.getId())
                        .orElseThrow()
                        .getReserved())
                .isZero();
        assertThat(orderRepository.count()).isZero();
        assertThat(cartItemRepository.countByCartId(cart.getId())).isEqualTo(2);
    }

    @Test
    void sameIdempotencyKey_ShouldCreateOneOrderAndOneReservation() throws Exception {
        ProductVariant variant = saveVariant("idempotent", 1);
        User user = saveCustomer("idempotent");
        UserAddress address = saveAddress(user);
        Cart cart = saveCart(user);
        saveItem(cart, variant, 1);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<Outcome> first = executor.submit(() -> invokeAfter(start, user, address, "same-key"));
        Future<Outcome> second = executor.submit(() -> invokeAfter(start, user, address, "same-key"));

        List<Outcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).extracting(Outcome::error).containsOnlyNulls();
        assertThat(outcomes).extracting(Outcome::replayed).containsExactlyInAnyOrder(false, true);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryRepository
                        .findByVariantIdWithVariant(variant.getId())
                        .orElseThrow()
                        .getReserved())
                .isEqualTo(1);
        assertThat(cartItemRepository.countByCartId(cart.getId())).isZero();
    }

    @Test
    void twoCustomersCompetingForLastUnit_ShouldNotOversell() throws Exception {
        ProductVariant variant = saveVariant("oversell", 1);
        User firstUser = saveCustomer("oversell-first");
        User secondUser = saveCustomer("oversell-second");
        UserAddress firstAddress = saveAddress(firstUser);
        UserAddress secondAddress = saveAddress(secondUser);
        Cart firstCart = saveCart(firstUser);
        Cart secondCart = saveCart(secondUser);
        saveItem(firstCart, variant, 1);
        saveItem(secondCart, variant, 1);
        CyclicBarrier start = new CyclicBarrier(2);

        Future<Outcome> first = executor.submit(() -> invokeAfter(start, firstUser, firstAddress, "first-key"));
        Future<Outcome> second = executor.submit(() -> invokeAfter(start, secondUser, secondAddress, "second-key"));

        List<Outcome> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

        assertThat(outcomes).extracting(Outcome::replayed).containsOnly(false);
        assertThat(outcomes.stream().filter(outcome -> outcome.error() == null)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> outcome.error() == ErrorCode.MALFORMED_REQUEST))
                .hasSize(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(inventoryRepository
                        .findByVariantIdWithVariant(variant.getId())
                        .orElseThrow()
                        .getReserved())
                .isEqualTo(1);
    }

    private Outcome invokeAfter(CyclicBarrier start, User user, UserAddress address, String key) {
        await(start);
        return invoke(user, address, key);
    }

    private Outcome invoke(User user, UserAddress address, String key) {
        try {
            var result = checkoutService.execute(user.getEmail(), request(address.getId()), key);
            return new Outcome(result.replayed(), null);
        } catch (ApplicationException exception) {
            return new Outcome(false, exception.getCode());
        }
    }

    private ProductVariant saveVariant(String suffix, long onHand) {
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Category " + suffix)
                .slug("category-" + suffix)
                .status(CategoryStatus.ACTIVE)
                .displayOrder(0)
                .build());
        Brand brand = brandRepository.saveAndFlush(Brand.builder()
                .name("Brand " + suffix)
                .status(BrandStatus.ACTIVE)
                .version(0L)
                .build());
        Product product = productRepository.saveAndFlush(Product.builder()
                .name("Product " + suffix)
                .slug("product-" + suffix)
                .published(true)
                .category(category)
                .brand(brand)
                .build());
        ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                .product(product)
                .sku("SKU-" + suffix)
                .combinationKey("default")
                .status(ProductVariantStatus.ACTIVE)
                .basePrice(new BigDecimal("10.00"))
                .build());
        InventoryBalance balance = InventoryBalance.zero(variant);
        balance.adjustOnHand(onHand);
        inventoryRepository.saveAndFlush(balance);
        return variant;
    }

    private User saveCustomer(String suffix) {
        return userRepository.saveAndFlush(User.builder()
                .name("Customer " + suffix)
                .email(suffix + "." + UUID.randomUUID() + "@example.test")
                .password("encoded")
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
    }

    private UserAddress saveAddress(User user) {
        UserAddress address = UserAddress.builder().user(user).build();
        address.replaceDetails("Buyer", "0123456789", "1 Main Street", "Ward 1", "District 1", "HCMC", "700000");
        return addressRepository.saveAndFlush(address);
    }

    private Cart saveCart(User user) {
        return cartRepository.saveAndFlush(Cart.forCustomer(user));
    }

    private void saveItem(Cart cart, ProductVariant variant, int quantity) {
        cartItemRepository.saveAndFlush(CartItem.builder()
                .id(new CartItemId(cart.getId(), variant.getId()))
                .cart(cart)
                .variant(variant)
                .quantity(quantity)
                .build());
    }

    private static CheckoutRequest request(Long addressId) {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddressId(addressId);
        return request;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not synchronize checkout callers", exception);
        }
    }

    private record Outcome(boolean replayed, ErrorCode error) {}

    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                UserRepository.class,
                UserAddressRepository.class,
                CartRepository.class,
                CartItemRepository.class,
                ProductRepository.class,
                ProductVariantRepository.class,
                CategoryRepository.class,
                BrandRepository.class,
                InventoryBalanceRepository.class,
                OrderRepository.class,
                PaymentRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(MYSQL.getJdbcUrl());
            dataSource.setUsername(MYSQL.getUsername());
            dataSource.setPassword(MYSQL.getPassword());
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.user.domain",
                    "com.xdpsx.ecommerce.cart.domain",
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
                    "com.xdpsx.ecommerce.catalog.product.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.inventory.domain",
                    "com.xdpsx.ecommerce.media.domain",
                    "com.xdpsx.ecommerce.order.domain",
                    "com.xdpsx.ecommerce.payment.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap().put("hibernate.jdbc.time_zone", "UTC");
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        CheckoutTransactionService checkoutTransactionService(
                UserRepository userRepository,
                UserAddressRepository userAddressRepository,
                CartRepository cartRepository,
                CartItemRepository cartItemRepository,
                ProductRepository productRepository,
                ProductVariantRepository productVariantRepository,
                InventoryBalanceRepository inventoryBalanceRepository,
                OrderRepository orderRepository,
                CheckoutProperties checkoutProperties,
                StorePricingProperties pricingProperties,
                Clock clock) {
            return new CheckoutTransactionService(
                    userRepository,
                    userAddressRepository,
                    cartRepository,
                    cartItemRepository,
                    productRepository,
                    productVariantRepository,
                    inventoryBalanceRepository,
                    orderRepository,
                    checkoutProperties,
                    pricingProperties,
                    clock);
        }

        @Bean
        CheckoutProperties checkoutProperties() {
            return new CheckoutProperties();
        }

        @Bean
        StorePricingProperties storePricingProperties() {
            StorePricingProperties properties = new StorePricingProperties();
            properties.setCurrency("VND");
            return properties;
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
