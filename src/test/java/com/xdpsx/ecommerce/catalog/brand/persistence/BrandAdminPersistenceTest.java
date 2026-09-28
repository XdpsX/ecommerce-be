package com.xdpsx.ecommerce.catalog.brand.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandFilter;
import com.xdpsx.ecommerce.catalog.brand.api.dto.AdminBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.brand.application.BrandServiceImpl;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/** Verifies the real paged Brand read path against MySQL and Hibernate statistics. */
@Testcontainers
@SpringJUnitConfig(BrandAdminPersistenceTest.PersistenceConfig.class)
class BrandAdminPersistenceTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("brand_admin_persistence")
            .withUsername("test")
            .withPassword("test")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                BrandRepository.class,
                CategoryRepository.class,
                MediaRepository.class,
                ProductRepository.class
            })
    static class PersistenceConfig {
        @Bean
        javax.sql.DataSource dataSource() {
            com.zaxxer.hikari.HikariConfig config = new com.zaxxer.hikari.HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(MYSQL.getJdbcUrl());
            config.setUsername(MYSQL.getUsername());
            config.setPassword(MYSQL.getPassword());
            config.setMaximumPoolSize(4);
            return new com.zaxxer.hikari.HikariDataSource(config);
        }

        @Bean
        liquibase.integration.spring.SpringLiquibase liquibase(javax.sql.DataSource dataSource) {
            liquibase.integration.spring.SpringLiquibase liquibase = new liquibase.integration.spring.SpringLiquibase();
            liquibase.setDataSource(dataSource);
            liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            return liquibase;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(
                javax.sql.DataSource dataSource, liquibase.integration.spring.SpringLiquibase liquibase) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.brand.domain",
                    "com.xdpsx.ecommerce.catalog.category.domain",
                    "com.xdpsx.ecommerce.catalog.product.domain",
                    "com.xdpsx.ecommerce.catalog.variantoption.domain",
                    "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "none");
            factory.getJpaPropertyMap()
                    .put(
                            "hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
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

        @Bean
        BrandService brandService(
                MediaRepository mediaRepository,
                BrandRepository brandRepository,
                CategoryRepository categoryRepository,
                ProductRepository productRepository) {
            return new BrandServiceImpl(mediaRepository, brandRepository, categoryRepository, productRepository);
        }
    }

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        transactionTemplate.executeWithoutResult(status -> {
            productRepository.deleteAll();
            productRepository.flush();
            brandRepository.deleteAll();
            brandRepository.flush();
            categoryRepository.deleteAll();
            categoryRepository.flush();
            mediaRepository.deleteAll();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            productRepository.deleteAll();
            productRepository.flush();
            brandRepository.deleteAll();
            brandRepository.flush();
            categoryRepository.deleteAll();
            categoryRepository.flush();
            mediaRepository.deleteAll();
        });
    }

    @Test
    void adminPage_ShouldPreserveMetadataAndBoundQueriesWithMultipleCategoryRows() {
        transactionTemplate.executeWithoutResult(status -> {
            Category shoes = categoryRepository.save(category("Shoes", "shoes", 0));
            Category sports = categoryRepository.save(category("Sports", "sports", 1));
            categoryRepository.flush();

            Media firstLogo = mediaRepository.save(media("logo-1"));
            Media secondLogo = mediaRepository.save(media("logo-2"));
            entityManager.flush();

            brandRepository.save(brand("Adidas", firstLogo, List.of(shoes, sports)));
            brandRepository.save(brand("Nike", secondLogo, List.of(shoes)));
            brandRepository.flush();
        });

        statistics.clear();
        AdminBrandFilter filter =
                AdminBrandFilter.builder().pageNum(1).pageSize(2).sort("name").build();

        PageResponse<AdminBrandResponse> result = brandService.getAdminBrands(filter);

        assertThat(result.meta().page()).isEqualTo(1);
        assertThat(result.meta().size()).isEqualTo(2);
        assertThat(result.meta().totalElements()).isEqualTo(2);
        assertThat(result.meta().totalPages()).isEqualTo(1);
        assertThat(result.data()).extracting(AdminBrandResponse::name).containsExactly("Adidas", "Nike");
        assertThat(result.data().get(0).categories()).hasSize(2);
        assertThat(result.data()).allSatisfy(brand -> assertThat(brand.image()).isNotNull());

        assertThat(statistics.getQueryExecutionCount()).isLessThanOrEqualTo(3);
        assertThat(statistics.getCollectionFetchCount()).isZero();
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    @Test
    void deleteBrand_ShouldRejectProductReferenceAndLeaveLogoAndBrandIntact() {
        Brand brand = transactionTemplate.execute(status -> {
            Media logo = mediaRepository.save(media("delete-logo"));
            Brand saved = brandRepository.saveAndFlush(brand("Delete Me", logo, List.of()));
            productRepository.saveAndFlush(Product.builder()
                    .name("Referenced product")
                    .slug("referenced-product")
                    .price(BigDecimal.TEN)
                    .brand(saved)
                    .build());
            return saved;
        });

        ApplicationException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> brandService.deleteBrand(
                        brand.getId(),
                        new com.xdpsx.ecommerce.catalog.brand.api.dto.DeleteBrandRequest(brand.getVersion())));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_IN_USE);
        transactionTemplate.executeWithoutResult(status -> {
            Brand persisted = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(persisted.getImage().getStatus()).isEqualTo(MediaStatus.ACTIVE);
            assertThat(productRepository.existsByBrandId(brand.getId())).isTrue();
        });
    }

    @Test
    void staleBrandWrite_ShouldRollbackAndKeepLogoActiveAfterOptimisticConflict() {
        Brand seeded = transactionTemplate.execute(status -> {
            Category category = categoryRepository.save(category("Optimistic Category", "optimistic-category", 0));
            categoryRepository.flush();
            Media logo = mediaRepository.save(media("optimistic-logo"));
            return brandRepository.saveAndFlush(brand("Optimistic Brand", logo, List.of(category)));
        });

        Brand stale = transactionTemplate.execute(status -> {
            Brand loaded = brandRepository.findById(seeded.getId()).orElseThrow();
            loaded.getCategories().size();
            entityManager.detach(loaded);
            return loaded;
        });

        transactionTemplate.executeWithoutResult(status -> {
            Brand current = brandRepository.findById(seeded.getId()).orElseThrow();
            current.setStatus(BrandStatus.INACTIVE);
            brandRepository.saveAndFlush(current);
        });

        ApplicationException staleRequest = org.junit.jupiter.api.Assertions.assertThrows(
                ApplicationException.class,
                () -> brandService.updateBrand(
                        seeded.getId(),
                        new com.xdpsx.ecommerce.catalog.brand.api.dto.UpdateBrandRequest(
                                "Optimistic Brand", BrandStatus.ACTIVE, null, null, stale.getVersion())));
        assertThat(staleRequest.getCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

        stale.setStatus(BrandStatus.ACTIVE);
        org.junit.jupiter.api.Assertions.assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> transactionTemplate.executeWithoutResult(status -> brandRepository.saveAndFlush(stale)));

        transactionTemplate.executeWithoutResult(status -> {
            Brand persisted = brandRepository.findDetailById(seeded.getId()).orElseThrow();
            assertThat(persisted.getStatus()).isEqualTo(BrandStatus.INACTIVE);
            assertThat(persisted.getImage().getStatus()).isEqualTo(MediaStatus.ACTIVE);
            assertThat(persisted.getCategories()).extracting(Category::getSlug).containsExactly("optimistic-category");
        });
    }

    private static Category category(String name, String slug, int order) {
        return Category.builder()
                .name(name)
                .slug(slug)
                .status(CategoryStatus.ACTIVE)
                .displayOrder(order)
                .build();
    }

    private static Media media(String id) {
        return Media.builder()
                .id(id)
                .externalId("external-" + id)
                .url("https://example.test/" + id + ".png")
                .contentType("image/png")
                .purpose(MediaPurpose.BRAND_LOGO)
                .status(MediaStatus.ACTIVE)
                .build();
    }

    private static Brand brand(String name, Media image, List<Category> categories) {
        return Brand.builder()
                .name(name)
                .status(BrandStatus.ACTIVE)
                .image(image)
                .categories(categories)
                .build();
    }
}
