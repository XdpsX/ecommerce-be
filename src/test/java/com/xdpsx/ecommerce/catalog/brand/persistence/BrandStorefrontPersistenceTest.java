package com.xdpsx.ecommerce.catalog.brand.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;

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

import com.xdpsx.ecommerce.catalog.brand.api.dto.StorefrontBrandResponse;
import com.xdpsx.ecommerce.catalog.brand.application.BrandService;
import com.xdpsx.ecommerce.catalog.brand.application.BrandServiceImpl;
import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.category.persistence.CategoryRepository;
import com.xdpsx.ecommerce.catalog.product.persistence.ProductRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/** Verifies the public Brand read model with a real Hibernate persistence context. */
@SpringJUnitConfig(BrandStorefrontPersistenceTest.PersistenceConfig.class)
class BrandStorefrontPersistenceTest {

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {BrandRepository.class, CategoryRepository.class, MediaRepository.class})
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:brand_storefront;DB_CLOSE_DELAY=-1;MODE=MySQL");
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

        @Bean
        ProductRepository productRepository() {
            return mock(ProductRepository.class);
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
    private BrandService brandService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        transactionTemplate.executeWithoutResult(status -> {
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
        });
    }

    @Test
    void globalRead_ShouldReturnOnlyActiveBrandsInStableOrder_WithOneImageFetchQuery() {
        transactionTemplate.executeWithoutResult(status -> {
            Media adidasLogo = mediaRepository.save(media("adidas-logo"));
            Media nikeLogo = mediaRepository.save(media("nike-logo"));
            brandRepository.save(brand("Nike", BrandStatus.ACTIVE, nikeLogo));
            brandRepository.save(brand("Adidas", BrandStatus.ACTIVE, adidasLogo));
            brandRepository.save(brand("Puma", BrandStatus.INACTIVE, null));
            brandRepository.flush();
        });

        statistics.clear();
        List<StorefrontBrandResponse> result = brandService.getStorefrontBrands(null);

        assertThat(result).extracting(StorefrontBrandResponse::name).containsExactly("Adidas", "Nike");
        assertThat(result)
                .extracting(StorefrontBrandResponse::image)
                .containsExactly("https://example.test/adidas-logo", "https://example.test/nike-logo");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityFetchCount()).isZero();
    }

    @Test
    void categoryRead_ShouldValidateVisibilityAndReturnOnlyDirectActiveAssociations() {
        Integer rootId = transactionTemplate
                .execute(status -> categoryRepository.saveAndFlush(
                        category("Electronics", "electronics", CategoryStatus.ACTIVE, null)))
                .getId();
        Integer targetId = transactionTemplate
                .execute(status -> categoryRepository.saveAndFlush(category(
                        "Laptops", "laptops", CategoryStatus.ACTIVE, categoryRepository.getReferenceById(rootId))))
                .getId();
        Integer childId = transactionTemplate
                .execute(status -> categoryRepository.saveAndFlush(category(
                        "Gaming", "gaming", CategoryStatus.ACTIVE, categoryRepository.getReferenceById(targetId))))
                .getId();

        transactionTemplate.executeWithoutResult(status -> {
            brandRepository.save(
                    brand("Direct", BrandStatus.ACTIVE, null, categoryRepository.getReferenceById(targetId)));
            brandRepository.save(
                    brand("Inactive", BrandStatus.INACTIVE, null, categoryRepository.getReferenceById(targetId)));
            brandRepository.save(
                    brand("Descendant", BrandStatus.ACTIVE, null, categoryRepository.getReferenceById(childId)));
            brandRepository.flush();
        });

        statistics.clear();
        List<StorefrontBrandResponse> result = brandService.getStorefrontBrands(targetId);

        assertThat(result).extracting(StorefrontBrandResponse::name).containsExactly("Direct");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void categoryRead_ShouldReturnNotFoundForHiddenCategoryWithoutReadingBrands() {
        Integer hiddenId = transactionTemplate
                .execute(status ->
                        categoryRepository.saveAndFlush(category("Retired", "retired", CategoryStatus.INACTIVE, null)))
                .getId();

        statistics.clear();
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.getStorefrontBrands(hiddenId));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void categoryRead_ShouldReturnNotFoundWhenAncestorIsHidden() {
        Integer hiddenRootId = transactionTemplate
                .execute(status -> categoryRepository.saveAndFlush(
                        category("Hidden root", "hidden-root", CategoryStatus.INACTIVE, null)))
                .getId();
        Integer visibleChildId = transactionTemplate
                .execute(status -> categoryRepository.saveAndFlush(category(
                        "Visible child",
                        "visible-child",
                        CategoryStatus.ACTIVE,
                        categoryRepository.getReferenceById(hiddenRootId))))
                .getId();

        statistics.clear();
        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.getStorefrontBrands(visibleChildId));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void categoryRead_ShouldReturnNotFoundForMissingCategoryWithoutReadingBrands() {
        statistics.clear();

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> brandService.getStorefrontBrands(999999));

        assertThat(exception.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private static Brand brand(String name, BrandStatus status, Media image, Category... categories) {
        return Brand.builder()
                .name(name)
                .status(status)
                .image(image)
                .categories(List.of(categories))
                .build();
    }

    private static Category category(String name, String slug, CategoryStatus status, Category parent) {
        return Category.builder()
                .name(name)
                .slug(slug)
                .status(status)
                .displayOrder(0)
                .parent(parent)
                .build();
    }

    private static Media media(String id) {
        return Media.builder()
                .id(id)
                .externalId("external-" + id)
                .url("https://example.test/" + id)
                .contentType("image/png")
                .purpose(MediaPurpose.BRAND_LOGO)
                .status(MediaStatus.ACTIVE)
                .build();
    }
}
