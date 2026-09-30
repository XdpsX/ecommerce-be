package com.xdpsx.ecommerce.inventory.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
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
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryAdjustmentRequest;
import com.xdpsx.ecommerce.inventory.api.dto.InventoryBalanceResponse;
import com.xdpsx.ecommerce.inventory.application.InventoryProvisioningService;
import com.xdpsx.ecommerce.inventory.application.InventoryProvisioningServiceImpl;
import com.xdpsx.ecommerce.inventory.application.InventoryService;
import com.xdpsx.ecommerce.inventory.application.InventoryServiceImpl;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.user.domain.AuthProvider;
import com.xdpsx.ecommerce.user.domain.Role;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@SpringJUnitConfig(InventoryPersistenceTest.PersistenceConfig.class)
class InventoryPersistenceTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                BrandRepository.class,
                CategoryRepository.class,
                ProductRepository.class,
                ProductVariantRepository.class,
                VariantOptionRepository.class,
                VariantOptionValueRepository.class,
                CartItemRepository.class,
                UserRepository.class,
                InventoryBalanceRepository.class,
                InventoryAdjustmentRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:inventory;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
                    "com.xdpsx.ecommerce.catalog.product.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.cart.domain",
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
        InventoryProvisioningService inventoryProvisioningService(InventoryBalanceRepository repository) {
            return new InventoryProvisioningServiceImpl(repository);
        }

        @Bean
        InventoryService inventoryService(
                InventoryBalanceRepository balanceRepository, InventoryAdjustmentRepository adjustmentRepository) {
            return new InventoryServiceImpl(balanceRepository, adjustmentRepository);
        }
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryAdjustmentRepository adjustmentRepository;

    @Autowired
    private InventoryProvisioningService provisioningService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            adjustmentRepository.deleteAll();
            balanceRepository.deleteAll();
            cartItemRepository.deleteAll();
            variantRepository.deleteAll();
            productRepository.deleteAll();
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    void newVariant_ShouldGetZeroBalanceAndAdjustmentShouldPersistAuditAtomically() {
        Long variantId = transactionTemplate.execute(status -> {
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Inventory product")
                    .slug("inventory-product")
                    .build());
            ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("INVENTORY-SKU")
                    .basePrice(BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("")
                    .build());
            provisioningService.provisionBalances(java.util.List.of(variant));
            return variant.getId();
        });

        InventoryBalanceResponse initial = inventoryService.getBalance(variantId);
        assertThat(initial).isEqualTo(new InventoryBalanceResponse(variantId, "INVENTORY-SKU", 0, 0, 0));

        InventoryBalanceResponse adjusted = inventoryService.adjustOnHand(
                variantId, new InventoryAdjustmentRequest(5L, "Initial receipt"), "admin@example.test");
        assertThat(adjusted).isEqualTo(new InventoryBalanceResponse(variantId, "INVENTORY-SKU", 5, 0, 5));

        assertThat(adjustmentRepository.findAllByVariantIdOrderByCreatedAtAscIdAsc(variantId))
                .singleElement()
                .satisfies(a -> {
                    assertThat(a.getPerformedBy()).isEqualTo("admin@example.test");
                    assertThat(a.getOnHandAfter()).isEqualTo(5);
                });
    }

    @Test
    void concurrentAdjustments_ShouldSerializeAndKeepBothAuditRows() throws Exception {
        Long variantId = transactionTemplate.execute(status -> {
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Concurrent inventory product")
                    .slug("concurrent-inventory-product")
                    .build());
            ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("CONCURRENT-INVENTORY-SKU")
                    .basePrice(BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("")
                    .build());
            provisioningService.provisionBalances(java.util.List.of(variant));
            return variant.getId();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<InventoryBalanceResponse> first = executor.submit(() ->
                    inventoryService.adjustOnHand(variantId, new InventoryAdjustmentRequest(5L, "first"), "admin-1"));
            Future<InventoryBalanceResponse> second = executor.submit(() ->
                    inventoryService.adjustOnHand(variantId, new InventoryAdjustmentRequest(7L, "second"), "admin-2"));

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryService.getBalance(variantId).onHand()).isEqualTo(12);
        assertThat(adjustmentRepository.findAllByVariantIdOrderByCreatedAtAscIdAsc(variantId))
                .hasSize(2);
    }

    @Test
    void cartStockQuery_ShouldUseActiveVariantInventoryAvailability() {
        Long userId = transactionTemplate.execute(status -> userRepository
                .saveAndFlush(User.builder()
                        .name("Customer")
                        .email("customer@example.test")
                        .role(Role.USER)
                        .authProvider(AuthProvider.LOCAL)
                        .build())
                .getId());
        Long[] variantIds = transactionTemplate.execute(status -> {
            Category category = categoryRepository.saveAndFlush(Category.builder()
                    .name("Inventory category")
                    .slug("inventory-category")
                    .status(CategoryStatus.ACTIVE)
                    .displayOrder(0)
                    .build());
            Brand brand = brandRepository.saveAndFlush(Brand.builder()
                    .name("Inventory brand")
                    .status(BrandStatus.ACTIVE)
                    .build());
            Product availableProduct = productRepository.saveAndFlush(Product.builder()
                    .name("Available product")
                    .slug("available-product")
                    .category(category)
                    .brand(brand)
                    .published(true)
                    .build());
            Product soldOutProduct = productRepository.saveAndFlush(Product.builder()
                    .name("Sold out product")
                    .slug("sold-out-product")
                    .category(category)
                    .brand(brand)
                    .published(true)
                    .build());
            ProductVariant availableVariant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(availableProduct)
                    .sku("AVAILABLE-CART-SKU")
                    .basePrice(BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("")
                    .build());
            ProductVariant soldOutVariant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(soldOutProduct)
                    .sku("SOLD-OUT-CART-SKU")
                    .basePrice(BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("")
                    .build());
            InventoryBalance availableBalance = InventoryBalance.zero(availableVariant);
            availableBalance.adjustOnHand(2);
            balanceRepository.saveAndFlush(availableBalance);
            balanceRepository.saveAndFlush(InventoryBalance.zero(soldOutVariant));
            cartItemRepository.save(CartItem.builder()
                    .id(new CartItemId(userId, availableVariant.getId()))
                    .user(userRepository.getReferenceById(userId))
                    .variant(availableVariant)
                    .quantity(1)
                    .build());
            cartItemRepository.save(CartItem.builder()
                    .id(new CartItemId(userId, soldOutVariant.getId()))
                    .user(userRepository.getReferenceById(userId))
                    .variant(soldOutVariant)
                    .quantity(1)
                    .build());
            return new Long[] {availableVariant.getId(), soldOutVariant.getId()};
        });

        assertThat(cartItemRepository.findInStockCartByUserId(userId))
                .extracting(item -> item.getVariant().getId())
                .containsExactly(variantIds[0]);
    }
}
