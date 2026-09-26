package com.xdpsx.ecommerce.catalog.category.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryFilter;
import com.xdpsx.ecommerce.catalog.category.api.dto.AdminCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.CategoryTreeResponse;
import com.xdpsx.ecommerce.catalog.category.api.dto.StorefrontCategoryResponse;
import com.xdpsx.ecommerce.catalog.category.application.CategoryHierarchy;
import com.xdpsx.ecommerce.catalog.category.application.CategoryService;
import com.xdpsx.ecommerce.catalog.category.application.CategoryServiceImpl;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/**
 * Executes the storefront read model against a real (in-memory) database.
 *
 * <p>
 * The properties under test only hold against a running JPA provider: the
 * ancestor chain must be fetched by the
 * read query itself rather than lazily (verified through Hibernate statistics),
 * and the stable sibling order must
 * come out of the executed SQL. H2 is not the production MySQL dialect, but
 * these queries are plain JPQL over
 * to-one associations, so no database-specific feature is involved.
 */
@SpringJUnitConfig(CategoryStorefrontPersistenceTest.PersistenceConfig.class)
class CategoryStorefrontPersistenceTest {

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = {CategoryRepository.class, MediaRepository.class})
    static class PersistenceConfig {

        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:category_storefront;DB_CLOSE_DELAY=-1;MODE=MySQL");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            // The Category aggregate plus the entities it associates with (image -> Media).
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.category.domain", "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            // Query-count assertions need the statistics registry, which is off by default.
            factory.getJpaPropertyMap().put("hibernate.generate_statistics", "true");
            factory.getJpaPropertyMap().put("hibernate.show_sql", "false");
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
        CategoryServiceImpl categoryService(
                CategoryRepository categoryRepository,
                MediaRepository mediaRepository,
                TransactionTemplate transactionTemplate) {
            return new CategoryServiceImpl(
                    categoryRepository,
                    mediaRepository,
                    new CategoryHierarchy(categoryRepository),
                    transactionTemplate);
        }
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private CategoryService categoryService;

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
        transactionTemplate.execute(status -> {
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
            return null;
        });
    }

    @AfterEach
    void cleanup() {
        transactionTemplate.execute(status -> {
            categoryRepository.deleteAll();
            mediaRepository.deleteAll();
            return null;
        });
    }

    private Integer persist(String name, CategoryStatus status, int displayOrder, Integer parentId) {
        return persist(name, status, displayOrder, parentId, null);
    }

    private Integer persist(String name, CategoryStatus status, int displayOrder, Integer parentId, String imageId) {
        if (imageId != null) {
            persistImage(imageId);
        }
        return transactionTemplate.execute(tx -> {
            Category category = Category.builder()
                    .name(name)
                    .slug(name.toLowerCase(java.util.Locale.ROOT))
                    .status(status)
                    .displayOrder(displayOrder)
                    .parent(parentId == null ? null : categoryRepository.getReferenceById(parentId))
                    .image(imageId == null ? null : entityManager.getReference(Media.class, imageId))
                    .build();
            return categoryRepository.saveAndFlush(category).getId();
        });
    }

    /**
     * Runs the read outside a transaction, so a lazy load would fail loudly instead
     * of passing silently.
     */
    private <T> T readWithoutSession(java.util.function.Supplier<T> read) {
        statistics.clear();
        T result = read.get();
        return result;
    }

    @Test
    void treeRead_ShouldAssembleEffectiveHierarchyInStableOrder_WithOneCategoryQuery() {
        // Arrange: three levels plus a stored-active subtree hidden by an inactive
        // parent.
        Integer electronics = persist("Electronics", CategoryStatus.ACTIVE, 1, null);
        Integer fashion = persist("Fashion", CategoryStatus.ACTIVE, 0, null);
        Integer retired = persist("Retired", CategoryStatus.INACTIVE, 2, null);
        Integer laptops = persist("Laptops", CategoryStatus.ACTIVE, 0, electronics);
        persist("Phones", CategoryStatus.ACTIVE, 1, electronics);
        persist("Gaming", CategoryStatus.ACTIVE, 0, laptops);
        persist("OldStock", CategoryStatus.ACTIVE, 0, retired);

        // Act
        List<CategoryTreeResponse> tree = readWithoutSession(() -> categoryService.getCategoryTree());

        // Assert: stable (displayOrder, id) order at every level; the hidden subtree is
        // absent everywhere.
        assertThat(tree.stream().map(CategoryTreeResponse::getName)).containsExactly("Fashion", "Electronics");
        assertThat(tree.get(1).getChildren().stream().map(CategoryTreeResponse::getName))
                .containsExactly("Laptops", "Phones");
        assertThat(tree.get(1).getChildren().get(0).getChildren().stream().map(CategoryTreeResponse::getName))
                .containsExactly("Gaming");

        // One flat Category read: the parent chain and images are fetched, so filtering
        // and mapping cannot
        // issue a lazy query per node.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void rootAndSlugReads_ShouldApplyEffectiveVisibility() {
        Integer electronics = persist("Electronics", CategoryStatus.ACTIVE, 0, null);
        persist("Phones", CategoryStatus.ACTIVE, 0, electronics);
        persist("Fashion", CategoryStatus.INACTIVE, 1, null);

        List<StorefrontCategoryResponse> roots =
                readWithoutSession(() -> categoryService.getStorefrontRootCategories());
        assertThat(roots.stream().map(StorefrontCategoryResponse::name)).containsExactly("Electronics");

        StorefrontCategoryResponse detail =
                readWithoutSession(() -> categoryService.getStorefrontCategoryBySlug("electronics"));
        assertThat(detail.name()).isEqualTo("Electronics");

        // A stored-inactive node and a missing slug share the same public not-found
        // semantics.
        assertThatThrownBy(() -> categoryService.getStorefrontCategoryBySlug("fashion"))
                .isInstanceOf(ApplicationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThatThrownBy(() -> categoryService.getStorefrontCategoryBySlug("unknown"))
                .isInstanceOf(ApplicationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void batchLookup_ShouldFetchAncestorChains_InOneQuery() {
        Integer electronics = persist("Electronics", CategoryStatus.ACTIVE, 0, null);
        Integer laptops = persist("Laptops", CategoryStatus.ACTIVE, 0, electronics);
        Integer gaming = persist("Gaming", CategoryStatus.ACTIVE, 0, laptops);

        statistics.clear();
        List<Category> categories = categoryRepository.findAllByIdInWithAncestry(List.of(laptops, gaming));

        assertThat(categories).hasSize(2);
        // Effective status can be derived for both without any further query.
        assertThat(categories.stream().map(Category::isEffectivelyActive)).containsExactly(true, true);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void adminPage_ShouldDeriveEffectiveFlags_WithoutPerRowLazyLoads() {
        // Arrange: several image-backed rows, so a missing image fetch would show up as one secondary select per
        // row. The stored-ACTIVE child under an INACTIVE parent must read as stored active but effectively inactive.
        Integer electronics = persist("Electronics", CategoryStatus.INACTIVE, 0, null, "img-electronics");
        persist("Laptops", CategoryStatus.ACTIVE, 0, electronics, "img-laptops");
        persist("Fashion", CategoryStatus.ACTIVE, 1, null, "img-fashion");

        statistics.clear();
        var page = categoryService.getAdminCategories(new AdminCategoryFilter());

        assertThat(page.data()).hasSize(3);
        var byName = page.data().stream()
                .collect(java.util.stream.Collectors.toMap(AdminCategoryResponse::name, row -> row));
        assertThat(byName.get("Electronics").effectivelyActive()).isFalse();
        assertThat(byName.get("Laptops").effectivelyActive()).isFalse();
        assertThat(byName.get("Laptops").status()).isEqualTo(CategoryStatus.ACTIVE);
        assertThat(byName.get("Fashion").effectivelyActive()).isTrue();
        // The image was fetched, not lazily selected per row.
        assertThat(byName.get("Fashion").image()).isEqualTo("https://example.test/img-fashion");

        // Bounded reads: the page query plus at most MAX_DEPTH - 1 chain-resolution queries — never one per row.
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    private void persistImage(String id) {
        transactionTemplate.execute(tx -> {
            entityManager.persist(Media.builder()
                    .id(id)
                    .externalId("ext-" + id)
                    .url("https://example.test/" + id)
                    .contentType("image/png")
                    .purpose(MediaPurpose.CATEGORY_IMAGE)
                    .status(MediaStatus.ACTIVE)
                    .build());
            entityManager.flush();
            return null;
        });
    }
}
