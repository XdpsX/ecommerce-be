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

import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
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
                    "com.xdpsx.ecommerce.inventory.domain",
                    "com.xdpsx.ecommerce.media.domain");
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
    private ProductVariantRepository variantRepository;

    @Autowired
    private InventoryBalanceRepository balanceRepository;

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
            variantRepository.deleteAll();
            productRepository.deleteAll();
        });
    }

    @Test
    void newVariant_ShouldGetZeroBalanceAndAdjustmentShouldPersistAuditAtomically() {
        Long variantId = transactionTemplate.execute(status -> {
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Inventory product")
                    .slug("inventory-product")
                    .price(BigDecimal.TEN)
                    .build());
            ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("INVENTORY-SKU")
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
                    .price(BigDecimal.TEN)
                    .build());
            ProductVariant variant = variantRepository.saveAndFlush(ProductVariant.builder()
                    .product(product)
                    .sku("CONCURRENT-INVENTORY-SKU")
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
}
