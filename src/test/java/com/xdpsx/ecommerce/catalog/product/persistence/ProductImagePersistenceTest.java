package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
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

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.brand.persistence.BrandRepository;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductImage;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/** Verifies ordered Product images with a real Hibernate persistence context. */
@SpringJUnitConfig(ProductImagePersistenceTest.PersistenceConfig.class)
class ProductImagePersistenceTest {

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                ProductRepository.class,
                MediaRepository.class,
                CategoryRepository.class,
                BrandRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:product_images;DB_CLOSE_DELAY=-1;MODE=MySQL");
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
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap().put("hibernate.generate_statistics", "true");
            factory.afterPropertiesSet();
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
    private MediaRepository mediaRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        transactionTemplate.executeWithoutResult(status -> {
            productRepository.deleteAll();
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
        });
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            productRepository.deleteAll();
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
        });
    }

    @Test
    void pageRead_ShouldFetchImagesAndMediaInOneBoundedQuery_WithStableOrder() {
        Seed seed = seedProducts();
        entityManager.clear();
        statistics.clear();

        List<Product> products = transactionTemplate.execute(
                status -> productRepository.findAllWithImagesByIdIn(List.of(seed.firstId(), seed.secondId())));

        assertThat(products).hasSize(2);
        assertThat(products.get(0).getImages())
                .extracting(image -> image.getMedia().getId())
                .containsExactly("media-1", "media-2");
        assertThat(products.get(1).getImages())
                .extracting(image -> image.getMedia().getId())
                .containsExactly("media-3");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    @Test
    void replaceImages_ShouldPersistReorderAndReplacementUnderUniqueConstraints() {
        Seed seed = seedProducts();
        Media replacement = transactionTemplate.execute(status -> {
            Media saved = mediaRepository.save(media("media-4"));
            entityManager.flush();
            return saved;
        });

        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findProductById(seed.firstId()).orElseThrow();
            product.getImages().clear();
            productRepository.flush();
            product.getImages()
                    .add(image(product, mediaRepository.findById("media-2").orElseThrow(), 0));
            product.getImages().add(image(product, replacement, 1));
            productRepository.saveAndFlush(product);
        });

        entityManager.clear();
        Product reloaded = transactionTemplate.execute(
                status -> productRepository.findProductById(seed.firstId()).orElseThrow());
        assertThat(reloaded.getImages())
                .extracting(image -> image.getMedia().getId())
                .containsExactly("media-2", "media-4");
        assertThat(reloaded.getImages())
                .extracting(ProductImage::getDisplayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void productImageFlow_ShouldHoldProductAndBulkMediaLocksAgainstCleanup() throws Exception {
        Seed seed = seedProducts();
        transactionTemplate.executeWithoutResult(status -> {
            mediaRepository.save(temporaryMedia("media-temp"));
            entityManager.flush();
        });

        CountDownLatch locksReady = new CountDownLatch(1);
        CountDownLatch releaseLocks = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> productImageUpdate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                productRepository.findByIdForUpdate(seed.firstId()).orElseThrow();
                mediaRepository.findAllByIdInForUpdate(List.of("media-temp"));
                locksReady.countDown();
                await(releaseLocks);
            }));
            assertThat(locksReady.await(20, TimeUnit.SECONDS)).isTrue();

            Future<Integer> cleanupClaim = executor.submit(() ->
                    transactionTemplate.execute(status -> mediaRepository.claimTemporaryForDeletion("media-temp")));
            assertThatThrownBy(() -> cleanupClaim.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

            releaseLocks.countDown();
            productImageUpdate.get(20, TimeUnit.SECONDS);
            assertThat(cleanupClaim.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            releaseLocks.countDown();
            executor.shutdownNow();
        }
    }

    private Seed seedProducts() {
        return transactionTemplate.execute(status -> {
            Category category = categoryRepository.save(category());
            Brand brand = brandRepository.save(brand());
            Media first = mediaRepository.save(media("media-1"));
            Media second = mediaRepository.save(media("media-2"));
            Media third = mediaRepository.save(media("media-3"));
            Product firstProduct = product("First", category, brand);
            firstProduct.getImages().add(image(firstProduct, first, 0));
            firstProduct.getImages().add(image(firstProduct, second, 1));
            Product secondProduct = product("Second", category, brand);
            secondProduct.getImages().add(image(secondProduct, third, 0));
            productRepository.save(firstProduct);
            productRepository.saveAndFlush(secondProduct);
            return new Seed(firstProduct.getId(), secondProduct.getId());
        });
    }

    private static Product product(String name, Category category, Brand brand) {
        return Product.builder()
                .name(name)
                .slug(name.toLowerCase())
                .price(java.math.BigDecimal.TEN)
                .category(category)
                .brand(brand)
                .build();
    }

    private static ProductImage image(Product product, Media media, int order) {
        return ProductImage.builder()
                .product(product)
                .media(media)
                .displayOrder(order)
                .build();
    }

    private static Category category() {
        return Category.builder()
                .name("Electronics")
                .slug("electronics")
                .status(CategoryStatus.ACTIVE)
                .displayOrder(0)
                .build();
    }

    private static Brand brand() {
        return Brand.builder()
                .name("Acme")
                .status(BrandStatus.ACTIVE)
                .version(0L)
                .build();
    }

    private static Media media(String id) {
        return Media.builder()
                .id(id)
                .externalId("external-" + id)
                .url("https://example.test/" + id)
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .status(MediaStatus.ACTIVE)
                .build();
    }

    private static Media temporaryMedia(String id) {
        return Media.builder()
                .id(id)
                .externalId("external-" + id)
                .url("https://example.test/" + id)
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .status(MediaStatus.TEMPORARY)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test participant was interrupted", exception);
        }
    }

    private record Seed(Long firstId, Long secondId) {}
}
