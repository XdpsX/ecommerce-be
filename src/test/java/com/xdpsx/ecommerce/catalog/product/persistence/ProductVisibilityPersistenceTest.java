package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelectionId;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOption;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionValue;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionRepository;
import com.xdpsx.ecommerce.catalog.variantoption.persistence.VariantOptionValueRepository;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@SpringJUnitConfig(ProductVisibilityPersistenceTest.PersistenceConfig.class)
class ProductVisibilityPersistenceTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaAuditing
    @EnableJpaRepositories(
            basePackageClasses = {
                ProductRepository.class,
                ProductVariantRepository.class,
                BrandRepository.class,
                CategoryRepository.class,
                VariantOptionRepository.class,
                VariantOptionValueRepository.class,
                InventoryBalanceRepository.class,
                MediaRepository.class
            })
    static class PersistenceConfig {
        @Bean
        DriverManagerDataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:product_visibility;DB_CLOSE_DELAY=-1;MODE=MySQL");
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
                    "com.xdpsx.ecommerce.inventory.domain",
                    "com.xdpsx.ecommerce.media.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "create-drop");
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
        ProductSpecification productSpecification() {
            return new ProductSpecification();
        }
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private VariantOptionRepository variantOptionRepository;

    @Autowired
    private VariantOptionValueRepository variantOptionValueRepository;

    @Autowired
    private InventoryBalanceRepository inventoryBalanceRepository;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private ProductSpecification productSpecification;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        transactionTemplate.executeWithoutResult(status -> {
            inventoryBalanceRepository.deleteAll();
            productVariantRepository.deleteAll();
            productRepository.deleteAll();
            variantOptionValueRepository.deleteAll();
            variantOptionRepository.deleteAll();
            mediaRepository.deleteAll();
            brandRepository.deleteAll();
            categoryRepository.deleteAll();
        });
    }

    @Test
    void storefrontVisibility_ShouldRequirePublishedActiveBrandActiveAncestryAndActiveVariant() {
        Seed seed = seedProducts();

        Page<Product> page = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility(), PageRequest.of(0, 10, Sort.by("id"))));

        assertThat(page).isNotNull();
        assertThat(page.getContent())
                .extracting(Product::getId)
                .containsExactlyInAnyOrder(seed.visibleProductId(), seed.secondVisibleProductId());

        var visible = transactionTemplate.execute(status -> productRepository.findStorefrontProductBySlug("visible"));
        var hidden =
                transactionTemplate.execute(status -> productRepository.findStorefrontProductBySlug("hidden-category"));
        assertThat(visible).isPresent();
        assertThat(hidden).isEmpty();
    }

    @Test
    void storefrontVisibility_ShouldKeepProductDistinctAcrossPagesWhenItHasMultipleVariants() {
        Seed seed = seedProducts();

        Page<Product> firstPage = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility(), PageRequest.of(0, 1, Sort.by("id"))));
        Page<Product> secondPage = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility(), PageRequest.of(1, 1, Sort.by("id"))));

        List<Long> ids = List.of(
                firstPage.getContent().get(0).getId(),
                secondPage.getContent().get(0).getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(2);
        assertThat(secondPage.getTotalElements()).isEqualTo(2);
        assertThat(new HashSet<>(ids)).hasSize(2);
        assertThat(ids).contains(seed.visibleProductId(), seed.secondVisibleProductId());
    }

    @Test
    void stockFilter_ShouldUseActiveVariantAvailabilityWithoutChangingVisibility() {
        Seed seed = seedProducts();
        transactionTemplate.executeWithoutResult(status -> {
            ProductVariant visibleVariant = productVariantRepository
                    .findByProductIdAndStatus(seed.visibleProductId(), ProductVariantStatus.ACTIVE)
                    .get(0);
            InventoryBalance visibleBalance = InventoryBalance.zero(visibleVariant);
            visibleBalance.adjustOnHand(5);
            inventoryBalanceRepository.saveAndFlush(visibleBalance);

            ProductVariant soldOutVariant = productVariantRepository
                    .findByProductIdAndStatus(seed.secondVisibleProductId(), ProductVariantStatus.ACTIVE)
                    .get(0);
            inventoryBalanceRepository.saveAndFlush(InventoryBalance.zero(soldOutVariant));

            Product soldOutProduct =
                    productRepository.findById(seed.secondVisibleProductId()).orElseThrow();
            ProductVariant inactiveVariant = productVariantRepository.saveAndFlush(ProductVariant.builder()
                    .product(soldOutProduct)
                    .sku("SECOND-INACTIVE")
                    .status(ProductVariantStatus.INACTIVE)
                    .combinationKey("inactive")
                    .build());
            InventoryBalance inactiveBalance = InventoryBalance.zero(inactiveVariant);
            inactiveBalance.adjustOnHand(10);
            inventoryBalanceRepository.saveAndFlush(inactiveBalance);
        });

        Page<Product> available = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.isInStock(true)),
                PageRequest.of(0, 10, Sort.by("id"))));
        Page<Product> unavailable = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.isInStock(false)),
                PageRequest.of(0, 10, Sort.by("id"))));

        assertThat(available.getContent()).extracting(Product::getId).containsExactly(seed.visibleProductId());
        assertThat(unavailable.getContent()).extracting(Product::getId).containsExactly(seed.secondVisibleProductId());
        assertThat(unavailable.getTotalElements()).isEqualTo(1);
    }

    private Seed seedProducts() {
        return transactionTemplate.execute(status -> {
            Category inactiveParent = categoryRepository.save(category("retired", CategoryStatus.INACTIVE, null));
            Category visibleCategory = categoryRepository.save(category("electronics", CategoryStatus.ACTIVE, null));
            Category hiddenCategory =
                    categoryRepository.save(category("hidden", CategoryStatus.ACTIVE, inactiveParent));
            Brand activeBrand = brandRepository.save(brand("Acme", BrandStatus.ACTIVE));
            Brand inactiveBrand = brandRepository.save(brand("Retired", BrandStatus.INACTIVE));
            VariantOption color = variantOptionRepository.save(option());
            VariantOptionValue black = variantOptionValueRepository.save(value(color, "black"));
            VariantOptionValue white = variantOptionValueRepository.save(value(color, "white"));

            Product visible = product("visible", visibleCategory, activeBrand, true);
            Product secondVisible = product("second-visible", visibleCategory, activeBrand, true);
            Product hiddenByCategory = product("hidden-category", hiddenCategory, activeBrand, true);
            Product hiddenByBrand = product("hidden-brand", visibleCategory, inactiveBrand, true);
            Product unpublished = product("unpublished", visibleCategory, activeBrand, false);
            Product noVariant = product("no-variant", visibleCategory, activeBrand, true);
            productRepository.saveAll(
                    List.of(visible, secondVisible, hiddenByCategory, hiddenByBrand, unpublished, noVariant));
            productRepository.flush();

            saveVariant(visible, "VISIBLE-BLACK", black, color);
            saveVariant(visible, "VISIBLE-WHITE", white, color);
            saveVariant(secondVisible, "SECOND-BLACK", black, color);
            saveVariant(hiddenByCategory, "HIDDEN-CATEGORY", black, color);
            saveVariant(hiddenByBrand, "HIDDEN-BRAND", black, color);
            saveVariant(unpublished, "UNPUBLISHED", black, color);
            return new Seed(visible.getId(), secondVisible.getId());
        });
    }

    private void saveVariant(Product product, String sku, VariantOptionValue value, VariantOption option) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .status(ProductVariantStatus.ACTIVE)
                .combinationKey(sku)
                .build();
        productVariantRepository.saveAndFlush(variant);
        variant.getSelections()
                .add(ProductVariantSelection.builder()
                        .id(new ProductVariantSelectionId(variant.getId(), option.getId()))
                        .variant(variant)
                        .optionValueId(value.getId())
                        .optionValue(value)
                        .build());
        productVariantRepository.saveAndFlush(variant);
    }

    private static Product product(String slug, Category category, Brand brand, boolean published) {
        return Product.builder()
                .name(slug)
                .slug(slug)
                .price(java.math.BigDecimal.TEN)
                .category(category)
                .brand(brand)
                .published(published)
                .build();
    }

    private static Category category(String slug, CategoryStatus status, Category parent) {
        return Category.builder()
                .name(slug)
                .slug(slug)
                .status(status)
                .displayOrder(0)
                .parent(parent)
                .build();
    }

    private static Brand brand(String name, BrandStatus status) {
        return Brand.builder().name(name).status(status).version(0L).build();
    }

    private static VariantOption option() {
        return VariantOption.builder()
                .code("color")
                .name("Color")
                .displayOrder(0)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private static VariantOptionValue value(VariantOption option, String code) {
        return VariantOptionValue.builder()
                .option(option)
                .code(code)
                .name(code)
                .displayOrder(code.equals("black") ? 0 : 1)
                .status(VariantOptionStatus.ACTIVE)
                .build();
    }

    private record Seed(Long visibleProductId, Long secondVisibleProductId) {}
}
