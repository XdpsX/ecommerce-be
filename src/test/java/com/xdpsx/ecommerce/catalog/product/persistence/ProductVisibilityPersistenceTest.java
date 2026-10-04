package com.xdpsx.ecommerce.catalog.product.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

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

            VariantOptionValue inactiveValue = variantOptionValueRepository.findAll().stream()
                    .filter(value -> value.getCode().equals("white"))
                    .findFirst()
                    .orElseThrow();
            inactiveValue.setStatus(VariantOptionStatus.INACTIVE);
            variantOptionValueRepository.saveAndFlush(inactiveValue);
            Product soldOutProduct =
                    productRepository.findById(seed.secondVisibleProductId()).orElseThrow();
            ProductVariant inactiveSelectionVariant = productVariantRepository.saveAndFlush(ProductVariant.builder()
                    .product(soldOutProduct)
                    .sku("SECOND-INACTIVE-VALUE")
                    .basePrice(java.math.BigDecimal.TEN)
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("inactive-value")
                    .build());
            inactiveSelectionVariant
                    .getSelections()
                    .add(ProductVariantSelection.builder()
                            .id(new ProductVariantSelectionId(
                                    inactiveSelectionVariant.getId(),
                                    inactiveValue.getOption().getId()))
                            .variant(inactiveSelectionVariant)
                            .optionValueId(inactiveValue.getId())
                            .optionValue(inactiveValue)
                            .build());
            productVariantRepository.saveAndFlush(inactiveSelectionVariant);
            InventoryBalance inactiveBalance = InventoryBalance.zero(inactiveSelectionVariant);
            inactiveBalance.adjustOnHand(10);
            inventoryBalanceRepository.saveAndFlush(inactiveBalance);
        });

        Page<Product> available = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.getStorefrontFiltersSpec(null, null, null, null, true, null, null, null, NOW),
                PageRequest.of(0, 10, Sort.by("id"))));
        Page<Product> unavailable = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.getStorefrontFiltersSpec(null, null, null, null, false, null, null, null, NOW),
                PageRequest.of(0, 10, Sort.by("id"))));

        assertThat(available.getContent()).extracting(Product::getId).containsExactly(seed.visibleProductId());
        assertThat(unavailable.getContent()).extracting(Product::getId).containsExactly(seed.secondVisibleProductId());
        assertThat(unavailable.getTotalElements()).isEqualTo(1);
    }

    @Test
    void combinedStorefrontFilters_ShouldMatchAvailabilityPriceAndOptionsOnTheSameVariant() {
        Seed seed = seedProducts();
        Long[] filterIds = transactionTemplate.execute(status -> {
            VariantOption color = variantOptionRepository.findAll().get(0);
            VariantOptionValue black = variantOptionValueRepository.findAll().stream()
                    .filter(value -> value.getCode().equals("black"))
                    .findFirst()
                    .orElseThrow();
            VariantOptionValue white = variantOptionValueRepository.findAll().stream()
                    .filter(value -> value.getCode().equals("white"))
                    .findFirst()
                    .orElseThrow();

            ProductVariant visibleBlack = findVariant(seed.visibleProductId(), "VISIBLE-BLACK");
            visibleBlack.changeBasePrice(new java.math.BigDecimal("200.00"));
            InventoryBalance blackBalance = InventoryBalance.zero(visibleBlack);
            blackBalance.adjustOnHand(5);
            inventoryBalanceRepository.saveAndFlush(blackBalance);

            ProductVariant visibleWhite = findVariant(seed.visibleProductId(), "VISIBLE-WHITE");
            visibleWhite.changeBasePrice(new java.math.BigDecimal("100.00"));
            inventoryBalanceRepository.saveAndFlush(InventoryBalance.zero(visibleWhite));
            productVariantRepository.saveAllAndFlush(List.of(visibleBlack, visibleWhite));

            ProductVariant matchingBlack = findVariant(seed.secondVisibleProductId(), "SECOND-BLACK");
            matchingBlack.changeBasePrice(new java.math.BigDecimal("100.00"));
            InventoryBalance matchingBalance = InventoryBalance.zero(matchingBlack);
            matchingBalance.adjustOnHand(2);
            inventoryBalanceRepository.saveAndFlush(matchingBalance);
            productVariantRepository.saveAndFlush(matchingBlack);
            return new Long[] {color.getId(), black.getId(), white.getId()};
        });

        var blackInStockFilter = productSpecification.getStorefrontFiltersSpec(
                null,
                null,
                null,
                new java.math.BigDecimal("150.00"),
                true,
                null,
                null,
                Map.of(filterIds[0], List.of(filterIds[1])),
                NOW);
        Page<Product> firstPage = transactionTemplate.execute(
                status -> productRepository.findAll(blackInStockFilter, PageRequest.of(0, 1, Sort.by("id"))));
        Page<Product> secondPage = transactionTemplate.execute(
                status -> productRepository.findAll(blackInStockFilter, PageRequest.of(1, 1, Sort.by("id"))));

        var whiteOutOfStockFilter = productSpecification.getStorefrontFiltersSpec(
                null,
                null,
                null,
                new java.math.BigDecimal("150.00"),
                false,
                null,
                null,
                Map.of(filterIds[0], List.of(filterIds[2])),
                NOW);
        Page<Product> whiteOutOfStock = transactionTemplate.execute(
                status -> productRepository.findAll(whiteOutOfStockFilter, PageRequest.of(0, 10, Sort.by("id"))));

        assertThat(firstPage.getContent()).extracting(Product::getId).containsExactly(seed.secondVisibleProductId());
        assertThat(firstPage.getTotalElements()).isEqualTo(1);
        assertThat(secondPage).isEmpty();
        assertThat(whiteOutOfStock.getContent()).extracting(Product::getId).containsExactly(seed.visibleProductId());
    }

    @Test
    void adminProductProjection_ShouldAggregateActiveVariantPriceAndInventoryForTheCurrentPage() {
        Seed seed = seedProducts();
        transactionTemplate.executeWithoutResult(status -> {
            ProductVariant black = findVariant(seed.visibleProductId(), "VISIBLE-BLACK");
            black.changeBasePrice(new java.math.BigDecimal("20.00"));
            InventoryBalance blackBalance = InventoryBalance.zero(black);
            blackBalance.adjustOnHand(5);
            blackBalance.reserve(2);
            inventoryBalanceRepository.saveAndFlush(blackBalance);

            ProductVariant white = findVariant(seed.visibleProductId(), "VISIBLE-WHITE");
            white.changeBasePrice(new java.math.BigDecimal("10.00"));
            InventoryBalance whiteBalance = InventoryBalance.zero(white);
            whiteBalance.adjustOnHand(4);
            whiteBalance.reserve(1);
            inventoryBalanceRepository.saveAndFlush(whiteBalance);

            Product product =
                    productRepository.findById(seed.visibleProductId()).orElseThrow();
            ProductVariant inactive = ProductVariant.builder()
                    .product(product)
                    .sku("VISIBLE-INACTIVE")
                    .basePrice(new java.math.BigDecimal("1.00"))
                    .status(ProductVariantStatus.INACTIVE)
                    .combinationKey("inactive")
                    .build();
            productVariantRepository.saveAndFlush(inactive);
            InventoryBalance inactiveBalance = InventoryBalance.zero(inactive);
            inactiveBalance.adjustOnHand(100);
            inventoryBalanceRepository.saveAndFlush(inactiveBalance);
            productVariantRepository.saveAllAndFlush(List.of(black, white));
        });

        Page<Product> firstPage = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.getAdminFiltersSpec(null, null, null), PageRequest.of(0, 1, Sort.by("id"))));
        Long productId = firstPage.getContent().get(0).getId();
        List<ProductVariantRepository.PriceRangeView> prices = transactionTemplate.execute(
                status -> productVariantRepository.findAdminPriceRanges(List.of(productId), NOW));
        List<InventoryBalanceRepository.ProductInventoryTotals> totals = transactionTemplate.execute(
                status -> inventoryBalanceRepository.findActiveProductInventoryTotals(List.of(productId)));

        assertThat(firstPage.getTotalElements()).isGreaterThan(1);
        assertThat(productId).isEqualTo(seed.visibleProductId());
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).getMinimumPrice()).isEqualByComparingTo("10.00");
        assertThat(prices.get(0).getMaximumPrice()).isEqualByComparingTo("20.00");
        assertThat(totals).hasSize(1);
        assertThat(totals.get(0).getOnHand()).isEqualTo(9L);
        assertThat(totals.get(0).getReserved()).isEqualTo(3L);
        assertThat(totals.get(0).getAvailable()).isEqualTo(6L);
    }

    @Test
    void priceRangeAndFilter_ShouldUseTheSameEligibleSkuForBothBounds() {
        Seed seed = seedProducts();
        transactionTemplate.executeWithoutResult(status -> {
            List<ProductVariant> variants = productVariantRepository.findByProductIdAndStatus(
                    seed.visibleProductId(), ProductVariantStatus.ACTIVE);
            variants.get(0).setBasePrice(new java.math.BigDecimal("10.00"));
            variants.get(1).setBasePrice(new java.math.BigDecimal("30.00"));
            productVariantRepository.saveAllAndFlush(variants);

            VariantOption retiredOption = variantOptionRepository.saveAndFlush(VariantOption.builder()
                    .code("retired")
                    .name("Retired")
                    .displayOrder(1)
                    .status(VariantOptionStatus.ACTIVE)
                    .build());
            VariantOptionValue retiredValue =
                    variantOptionValueRepository.saveAndFlush(value(retiredOption, "retired"));
            Product visible =
                    productRepository.findById(seed.visibleProductId()).orElseThrow();
            ProductVariant invalid = ProductVariant.builder()
                    .product(visible)
                    .sku("VISIBLE-RETIRED")
                    .basePrice(new java.math.BigDecimal("1.00"))
                    .status(ProductVariantStatus.ACTIVE)
                    .combinationKey("retired")
                    .build();
            invalid.getSelections()
                    .add(ProductVariantSelection.builder()
                            .id(new ProductVariantSelectionId(invalid.getId(), retiredOption.getId()))
                            .variant(invalid)
                            .optionValueId(retiredValue.getId())
                            .optionValue(retiredValue)
                            .build());
            productVariantRepository.saveAndFlush(invalid);
            retiredValue.setStatus(VariantOptionStatus.INACTIVE);
            variantOptionValueRepository.saveAndFlush(retiredValue);
        });

        ProductVariantRepository.PriceRangeView range = transactionTemplate.execute(status -> productVariantRepository
                .findEligiblePriceRanges(List.of(seed.visibleProductId()))
                .get(0));
        Page<Product> matching = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification
                        .storefrontVisibility()
                        .and(productSpecification.hasPriceInRange(
                                new java.math.BigDecimal("25.00"), new java.math.BigDecimal("35.00"))),
                PageRequest.of(0, 10, Sort.by("id"))));
        Page<Product> notMatching = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification
                        .storefrontVisibility()
                        .and(productSpecification.hasPriceInRange(
                                new java.math.BigDecimal("15.00"), new java.math.BigDecimal("25.00"))),
                PageRequest.of(0, 10, Sort.by("id"))));

        assertThat(range.getMinimumPrice()).isEqualByComparingTo("10.00");
        assertThat(range.getMaximumPrice()).isEqualByComparingTo("30.00");
        assertThat(matching.getContent()).extracting(Product::getId).containsExactly(seed.visibleProductId());
        assertThat(notMatching).isEmpty();
    }

    @Test
    void effectivePriceQueries_ShouldResolveActiveFutureAndExpiredSalesAtCapturedInstant() {
        Seed seed = seedProducts();
        transactionTemplate.executeWithoutResult(status -> {
            List<ProductVariant> visibleVariants = productVariantRepository.findByProductIdAndStatus(
                    seed.visibleProductId(), ProductVariantStatus.ACTIVE);
            ProductVariant activeSale = visibleVariants.stream()
                    .filter(variant -> variant.getSku().equals("VISIBLE-BLACK"))
                    .findFirst()
                    .orElseThrow();
            activeSale.replaceSaleSchedule(
                    new java.math.BigDecimal("5.00"), NOW.minusSeconds(60), NOW.plusSeconds(60), NOW);
            ProductVariant futureSale = visibleVariants.stream()
                    .filter(variant -> variant.getSku().equals("VISIBLE-WHITE"))
                    .findFirst()
                    .orElseThrow();
            futureSale.replaceSaleSchedule(
                    new java.math.BigDecimal("1.00"), NOW.plusSeconds(60), NOW.plusSeconds(120), NOW);
            productVariantRepository.saveAllAndFlush(visibleVariants);

            ProductVariant expiredSale = productVariantRepository
                    .findByProductIdAndStatus(seed.secondVisibleProductId(), ProductVariantStatus.ACTIVE)
                    .get(0);
            expiredSale.setBasePrice(new java.math.BigDecimal("20.00"));
            expiredSale.setSalePrice(new java.math.BigDecimal("2.00"));
            expiredSale.setSaleStartsAt(NOW.minusSeconds(120));
            expiredSale.setSaleEndsAt(NOW.minusSeconds(60));
            productVariantRepository.saveAndFlush(expiredSale);
        });

        List<ProductVariantRepository.PriceRangeView> ranges =
                transactionTemplate.execute(status -> productVariantRepository.findEligiblePriceRanges(
                        List.of(seed.visibleProductId(), seed.secondVisibleProductId()), NOW));
        ProductVariantRepository.PriceRangeView visibleRange = ranges.stream()
                .filter(range -> range.getProductId().equals(seed.visibleProductId()))
                .findFirst()
                .orElseThrow();
        ProductVariantRepository.PriceRangeView secondRange = ranges.stream()
                .filter(range -> range.getProductId().equals(seed.secondVisibleProductId()))
                .findFirst()
                .orElseThrow();

        Page<Product> matching = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification
                        .storefrontVisibility()
                        .and(productSpecification.hasPriceInRange(
                                new java.math.BigDecimal("5.00"), new java.math.BigDecimal("5.00"), NOW)),
                PageRequest.of(0, 10, Sort.by("id"))));
        Page<Product> sorted = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.getSortSpec("price", NOW)),
                PageRequest.of(0, 10)));

        assertThat(visibleRange.getMinimumPrice()).isEqualByComparingTo("5.00");
        assertThat(visibleRange.getMaximumPrice()).isEqualByComparingTo("10.00");
        assertThat(secondRange.getMinimumPrice()).isEqualByComparingTo("20.00");
        assertThat(secondRange.getMaximumPrice()).isEqualByComparingTo("20.00");
        assertThat(matching.getContent()).extracting(Product::getId).containsExactly(seed.visibleProductId());
        assertThat(sorted.getContent())
                .extracting(Product::getId)
                .containsExactly(seed.visibleProductId(), seed.secondVisibleProductId());
    }

    @Test
    void priceSort_ShouldUseEligibleMinimumAndStableProductIdTieBreakAcrossPages() {
        Seed seed = seedProducts();
        Long thirdProductId = transactionTemplate.execute(status -> {
            List<ProductVariant> visibleVariants = productVariantRepository.findByProductIdAndStatus(
                    seed.visibleProductId(), ProductVariantStatus.ACTIVE);
            visibleVariants.forEach(variant -> variant.setBasePrice(new java.math.BigDecimal("20.00")));
            productVariantRepository.saveAllAndFlush(visibleVariants);
            ProductVariant secondVariant = productVariantRepository
                    .findByProductIdAndStatus(seed.secondVisibleProductId(), ProductVariantStatus.ACTIVE)
                    .get(0);
            secondVariant.setBasePrice(new java.math.BigDecimal("20.00"));
            productVariantRepository.saveAndFlush(secondVariant);

            Product source = productRepository.findById(seed.visibleProductId()).orElseThrow();
            Product third = productRepository.saveAndFlush(
                    product("third-price", source.getCategory(), source.getBrand(), true));
            VariantOption color = variantOptionRepository.findAll().stream()
                    .filter(option -> option.getCode().equals("color"))
                    .findFirst()
                    .orElseThrow();
            VariantOptionValue black = variantOptionValueRepository.findAll().stream()
                    .filter(value -> value.getCode().equals("black"))
                    .findFirst()
                    .orElseThrow();
            saveVariant(third, "THIRD-PRICE", black, color).setBasePrice(new java.math.BigDecimal("10.00"));
            productVariantRepository.flush();
            return third.getId();
        });

        Page<Product> ascending = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.getSortSpec("price")),
                PageRequest.of(0, 3)));
        Page<Product> descending = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.getSortSpec("-price")),
                PageRequest.of(0, 3)));
        Page<Product> firstPage = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.getSortSpec("price")),
                PageRequest.of(0, 2)));
        Page<Product> secondPage = transactionTemplate.execute(status -> productRepository.findAll(
                productSpecification.storefrontVisibility().and(productSpecification.getSortSpec("price")),
                PageRequest.of(1, 2)));

        assertThat(ascending.getContent())
                .extracting(Product::getId)
                .containsExactly(thirdProductId, seed.visibleProductId(), seed.secondVisibleProductId());
        assertThat(descending.getContent())
                .extracting(Product::getId)
                .containsExactly(seed.visibleProductId(), seed.secondVisibleProductId(), thirdProductId);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(secondPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent())
                .extracting(Product::getId)
                .containsExactly(thirdProductId, seed.visibleProductId());
        assertThat(secondPage.getContent()).extracting(Product::getId).containsExactly(seed.secondVisibleProductId());
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

    private ProductVariant findVariant(Long productId, String sku) {
        return productVariantRepository.findByProductIdAndStatus(productId, ProductVariantStatus.ACTIVE).stream()
                .filter(variant -> variant.getSku().equals(sku))
                .findFirst()
                .orElseThrow();
    }

    private ProductVariant saveVariant(Product product, String sku, VariantOptionValue value, VariantOption option) {
        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(sku)
                .basePrice(java.math.BigDecimal.TEN)
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
        return variant;
    }

    private static Product product(String slug, Category category, Brand brand, boolean published) {
        return Product.builder()
                .name(slug)
                .slug(slug)
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
