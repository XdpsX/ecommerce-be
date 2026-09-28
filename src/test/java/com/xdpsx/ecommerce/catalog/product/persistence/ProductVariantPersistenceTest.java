package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

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
import com.xdpsx.ecommerce.catalog.product.api.dto.UpdateProductVariantStatusRequest;
import com.xdpsx.ecommerce.catalog.product.application.ProductVariantServiceImpl;
import com.xdpsx.ecommerce.catalog.product.domain.*;
import com.xdpsx.ecommerce.catalog.variantoption.api.dto.UpdateVariantOptionValueRequest;
import com.xdpsx.ecommerce.catalog.variantoption.application.VariantOptionServiceImpl;
import com.xdpsx.ecommerce.catalog.variantoption.domain.*;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;

/** Verifies that SKU selections persist as one value per option and retain their value identity. */
@SpringJUnitConfig(ProductVariantPersistenceTest.PersistenceConfig.class)
class ProductVariantPersistenceTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                ProductRepository.class,
                ProductVariantRepository.class,
                VariantOptionRepository.class,
                VariantOptionValueRepository.class,
                CategoryRepository.class,
                BrandRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:product_variants;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=10000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.product.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
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
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private VariantOptionRepository optionRepository;

    @Autowired
    private VariantOptionValueRepository valueRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clearData() {
        transactionTemplate.executeWithoutResult(status -> {
            variantRepository.deleteAll();
            productRepository.deleteAll();
            valueRepository.deleteAll();
            optionRepository.deleteAll();
        });
    }

    @Test
    void variantSelections_ShouldPersistOptionValueIdentityAndStableOrder() {
        Long productId = transactionTemplate.execute(status -> {
            VariantOption color = optionRepository.saveAndFlush(option("color", 0));
            VariantOption size = optionRepository.saveAndFlush(option("size", 1));
            VariantOptionValue black = valueRepository.save(value(color, "black", 0));
            VariantOptionValue medium = valueRepository.saveAndFlush(value(size, "medium", 0));
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Shirt")
                    .slug("shirt")
                    .price(java.math.BigDecimal.TEN)
                    .build());
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku("SHIRT-BLACK-M")
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey(color.getId() + "=" + black.getId() + "|" + size.getId() + "=" + medium.getId())
                    .build();
            variant.getSelections().add(selection(variant, color, black));
            variant.getSelections().add(selection(variant, size, medium));
            variantRepository.saveAndFlush(variant);
            return product.getId();
        });

        List<ProductVariant> variants =
                transactionTemplate.execute(status -> variantRepository.findAllWithSelectionsByProductId(productId));
        List<String> optionValueCodes = transactionTemplate.execute(
                status -> variantRepository.findAllWithSelectionsByProductId(productId).get(0).getSelections().stream()
                        .map(selection -> selection.getOptionValue().getCode())
                        .toList());

        assertThat(variants).hasSize(1);
        assertThat(optionValueCodes).containsExactly("black", "medium");
        assertThat(variants.get(0).getSelections())
                .extracting(ProductVariantSelection::getOptionValueId)
                .containsExactly(
                        variants.get(0).getSelections().get(0).getOptionValueId(),
                        variants.get(0).getSelections().get(1).getOptionValueId());
    }

    @Test
    void duplicateCombination_ShouldRollBackTheWholeVariantBatch() {
        Product product = transactionTemplate.execute(status -> productRepository.saveAndFlush(Product.builder()
                .name("Simple product")
                .slug("simple-product")
                .price(java.math.BigDecimal.TEN)
                .build()));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    variantRepository.save(variant(product, "SIMPLE-1"));
                    variantRepository.saveAndFlush(variant(product, "SIMPLE-2"));
                }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(variantRepository.count()).isZero();
    }

    @Test
    void concurrentDuplicateSku_ShouldRollBackTheLosingBatch() throws Exception {
        Long productId = transactionTemplate.execute(status -> productRepository
                .saveAndFlush(Product.builder()
                        .name("Concurrent product")
                        .slug("concurrent-product")
                        .price(java.math.BigDecimal.TEN)
                        .build())
                .getId());
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> outcomes = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        try {
                            transactionTemplate.executeWithoutResult(status -> {
                                Product product =
                                        productRepository.findById(productId).orElseThrow();
                                await(barrier);
                                variantRepository.saveAllAndFlush(List.of(
                                        variant(product, "RACE-SKU", "race-shared-" + index),
                                        variant(product, "RACE-UNIQUE-" + index, "race-unique-" + index)));
                            });
                            return true;
                        } catch (RuntimeException exception) {
                            return false;
                        }
                    }))
                    .toList();

            List<Boolean> results =
                    outcomes.stream().map(outcome -> getOutcome(outcome)).toList();
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(variantRepository.count()).isEqualTo(2);
            List<String> skus = variantRepository.findAll().stream()
                    .map(ProductVariant::getSku)
                    .toList();
            assertThat(skus).contains("RACE-SKU").anyMatch(sku -> sku.startsWith("RACE-UNIQUE-"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void activation_ShouldSerializeWithOptionValueDeactivation() throws Exception {
        Long[] ids = transactionTemplate.execute(status -> {
            VariantOption color = optionRepository.saveAndFlush(option("concurrent-color", 0));
            VariantOptionValue black = valueRepository.saveAndFlush(value(color, "concurrent-black", 0));
            Product product = productRepository.saveAndFlush(Product.builder()
                    .name("Activation product")
                    .slug("activation-product")
                    .price(java.math.BigDecimal.TEN)
                    .build());
            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .sku("ACTIVATION-SKU")
                    .status(ProductVariantStatus.INACTIVE)
                    .combinationKey(color.getId() + "=" + black.getId())
                    .build();
            variant.getSelections().add(selection(variant, color, black));
            variantRepository.saveAndFlush(variant);
            return new Long[] {product.getId(), variant.getId(), color.getId(), black.getId()};
        });
        ProductVariantServiceImpl variantService = new ProductVariantServiceImpl(
                productRepository, variantRepository, optionRepository, valueRepository, entityManager);
        VariantOptionServiceImpl optionService =
                new VariantOptionServiceImpl(optionRepository, valueRepository, variantRepository);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch deactivationReady = new CountDownLatch(1);
        CountDownLatch releaseDeactivation = new CountDownLatch(1);
        try {
            Future<?> deactivation = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                optionService.updateValue(
                        ids[2], ids[3], new UpdateVariantOptionValueRequest("Black", 0, VariantOptionStatus.INACTIVE));
                deactivationReady.countDown();
                await(releaseDeactivation);
            }));
            assertThat(deactivationReady.await(20, TimeUnit.SECONDS)).isTrue();

            Future<Object> activation = executor.submit(() -> {
                try {
                    return transactionTemplate.execute(status -> variantService.updateStatus(
                            ids[0], ids[1], new UpdateProductVariantStatusRequest(ProductVariantStatus.ACTIVE)));
                } catch (RuntimeException exception) {
                    return exception;
                }
            });

            assertThatThrownBy(() -> activation.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            releaseDeactivation.countDown();
            deactivation.get(20, TimeUnit.SECONDS);
            Object activationResult = activation.get(20, TimeUnit.SECONDS);
            assertThat(activationResult).isInstanceOf(com.xdpsx.ecommerce.common.error.ApplicationException.class);
            assertThat(((com.xdpsx.ecommerce.common.error.ApplicationException) activationResult).getCode())
                    .isEqualTo(com.xdpsx.ecommerce.common.error.ErrorCode.VALIDATION_FAILED);
            ProductVariantStatus persistedVariantStatus = transactionTemplate.execute(status -> variantRepository
                    .findByIdAndProductIdWithSelections(ids[1], ids[0])
                    .orElseThrow()
                    .getStatus());
            VariantOptionStatus persistedValueStatus = transactionTemplate.execute(
                    status -> valueRepository.findById(ids[3]).orElseThrow().getStatus());
            assertThat(persistedVariantStatus).isEqualTo(ProductVariantStatus.INACTIVE);
            assertThat(persistedValueStatus).isEqualTo(VariantOptionStatus.INACTIVE);
        } finally {
            releaseDeactivation.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent test participants did not rendezvous", exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test participant was interrupted", exception);
        }
    }

    private static boolean getOutcome(Future<Boolean> outcome) {
        try {
            return outcome.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent test participant did not finish", exception);
        }
    }

    private static VariantOption option(String code, int order) {
        return VariantOption.builder()
                .code(code)
                .name(code)
                .displayOrder(order)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static VariantOptionValue value(VariantOption option, String code, int order) {
        return VariantOptionValue.builder()
                .option(option)
                .code(code)
                .name(code)
                .displayOrder(order)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static ProductVariantSelection selection(
            ProductVariant variant, VariantOption option, VariantOptionValue value) {
        return ProductVariantSelection.builder()
                .id(new ProductVariantSelectionId(null, option.getId()))
                .variant(variant)
                .optionValueId(value.getId())
                .optionValue(value)
                .build();
    }

    private static ProductVariant variant(Product product, String sku) {
        return variant(product, sku, "");
    }

    private static ProductVariant variant(Product product, String sku, String combinationKey) {
        return ProductVariant.builder()
                .product(product)
                .sku(sku)
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey(combinationKey)
                .build();
    }
}
