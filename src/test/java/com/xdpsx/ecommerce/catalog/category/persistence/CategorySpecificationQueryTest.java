package com.xdpsx.ecommerce.catalog.category.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;

/**
 * Executes the admin Category specification through Hibernate against a real (in-memory) database.
 *
 * <p>The point of this test is that the query is actually built and run. Comparing the {@code parent} association
 * to a raw {@code Integer} only fails at query time, so a mocked {@code CriteriaBuilder} cannot protect it.
 *
 * <p>Only the Category domain is mapped. H2 is not the production MySQL dialect, but the failure mode under test
 * comes from Hibernate's criteria rendering rather than from a database-specific feature.
 */
@SpringJUnitConfig(CategorySpecificationQueryTest.PersistenceConfig.class)
class CategorySpecificationQueryTest {

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = CategoryRepository.class)
    static class PersistenceConfig {

        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:category_spec;DB_CLOSE_DELAY=-1;MODE=MySQL");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DriverManagerDataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            // The Category aggregate plus the entities it associates with. Scanning only Category would leave
            // its Media association without a mapped target.
            factory.setPackagesToScan(
                    "com.xdpsx.ecommerce.catalog.category.domain", "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
            factory.getJpaPropertyMap().put("hibernate.show_sql", "false");
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }

    @Autowired
    private CategoryRepository categoryRepository;

    private Integer electronicsId;
    private Integer fashionId;

    @BeforeEach
    void seed() {
        electronicsId = persist("Electronics", CategoryStatus.ACTIVE, null);
        fashionId = persist("Fashion", CategoryStatus.ACTIVE, null);
        persist("Laptops", CategoryStatus.ACTIVE, electronicsId);
        persist("Phones", CategoryStatus.INACTIVE, electronicsId);
        persist("Shirts", CategoryStatus.ACTIVE, fashionId);
    }

    @AfterEach
    void cleanup() {
        categoryRepository.deleteAll();
    }

    private Integer persist(String name, CategoryStatus status, Integer parentId) {
        Category category = Category.builder()
                .name(name)
                .slug(name.toLowerCase(java.util.Locale.ROOT))
                .status(status)
                .displayOrder(0)
                .parent(
                        parentId == null
                                ? null
                                : categoryRepository.findById(parentId).orElseThrow())
                .build();
        return categoryRepository.save(category).getId();
    }

    private List<String> names(Specification<Category> spec) {
        List<Category> categories = categoryRepository.findAll(spec);
        // Read the lazy parent so a broken fetch cannot pass silently.
        categories.forEach(category -> {
            if (category.getParent() != null) category.getParent().getId();
        });
        return categories.stream().map(Category::getName).toList();
    }

    private Specification<Category> adminSpec(String name, CategoryStatus status, Integer parentId) {
        return CategorySpecification.getInstance().buildAdminCategoriesSpec(name, status, parentId, null, null);
    }

    @Test
    void parentIdFilter_ShouldReturnOnlyDirectChildrenOfThatParent() {
        assertThat(names(adminSpec(null, null, electronicsId))).containsExactlyInAnyOrder("Laptops", "Phones");
    }

    @Test
    void parentIdFilter_ShouldExcludeOtherParentsAndRoots() {
        assertThat(names(adminSpec(null, null, fashionId))).containsExactly("Shirts");
    }

    @Test
    void parentIdFilter_ShouldCombineWithStatus() {
        assertThat(names(adminSpec(null, CategoryStatus.ACTIVE, electronicsId))).containsExactly("Laptops");
    }

    @Test
    void statusFilter_ShouldReturnMatchesAcrossParents() {
        assertThat(names(adminSpec(null, CategoryStatus.INACTIVE, null))).containsExactly("Phones");
    }

    @Test
    void nameFilter_ShouldStillCombineWithParentId() {
        assertThat(names(adminSpec("Lap", null, electronicsId))).containsExactly("Laptops");
    }

    @Test
    void noFilter_ShouldReturnAllRowsIncludingRoots() {
        assertThat(names(adminSpec(null, null, null))).hasSize(5);
    }

    @Test
    void treeSpec_ShouldReturnOnlyActiveChildrenOfTheParent() {
        Category electronics = categoryRepository.findById(electronicsId).orElseThrow();

        assertThat(names(CategorySpecification.getInstance().buildCategoryTreeSpec(electronics, null)))
                .containsExactly("Laptops");
    }

    @Test
    void treeSpec_ShouldReturnOnlyActiveRoots() {
        assertThat(names(CategorySpecification.getInstance().buildCategoryTreeSpec(null, null)))
                .containsExactlyInAnyOrder("Electronics", "Fashion");
    }
}
