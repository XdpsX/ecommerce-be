package com.xdpsx.ecommerce.catalog.product.persistence;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.shared.persistence.BasicSpecification;
import com.xdpsx.ecommerce.catalog.variantoption.domain.VariantOptionStatus;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;

@Component
public class ProductSpecification extends BasicSpecification<Product> {

    public Specification<Product> getAdminFiltersSpec(String search, String sort, Boolean hasPublished) {
        return hasName(search).and(getSortSpec(sort)).and(hasPublished(hasPublished));
    }

    public Specification<Product> getStorefrontFiltersSpec(
            String search,
            String sort,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean inStock,
            Integer categoryId,
            Integer brandId,
            Map<Long, List<Long>> optionValueIdsByOption) {
        return storefrontVisibility()
                .and(hasName(search))
                .and(getSortSpec(sort))
                .and(hasPriceInRange(minPrice, maxPrice))
                .and(isInStock(inStock))
                .and(belongsToCategory(categoryId))
                .and(belongsToBrand(brandId))
                .and(hasMatchingActiveVariant(optionValueIdsByOption));
    }

    public Specification<Product> storefrontVisibility() {
        return hasPublished(true)
                .and(hasActiveBrand())
                .and(hasEffectivelyActiveCategory())
                .and(hasEligibleVariant());
    }

    public Specification<Product> hasActiveBrand() {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("brand").get("status"), BrandStatus.ACTIVE);
    }

    public Specification<Product> hasEffectivelyActiveCategory() {
        return (root, query, criteriaBuilder) -> {
            Join<Product, Category> category = root.join("category");
            Join<Category, Category> parent = category.join("parent", JoinType.LEFT);
            Join<Category, Category> grandparent = parent.join("parent", JoinType.LEFT);
            Join<Category, Category> greatGrandparent = grandparent.join("parent", JoinType.LEFT);
            return criteriaBuilder.and(
                    criteriaBuilder.equal(category.get("status"), CategoryStatus.ACTIVE),
                    activeOrMissing(criteriaBuilder, parent),
                    activeOrMissing(criteriaBuilder, grandparent),
                    criteriaBuilder.isNull(greatGrandparent.get("id")));
        };
    }

    public Specification<Product> hasActiveVariant() {
        return (root, query, criteriaBuilder) -> {
            var subquery = query.subquery(Long.class);
            var variant = subquery.from(ProductVariant.class);
            subquery.select(variant.get("id"));
            subquery.where(
                    criteriaBuilder.equal(variant.get("product").get("id"), root.get("id")),
                    criteriaBuilder.equal(variant.get("status"), ProductVariantStatus.ACTIVE));
            return criteriaBuilder.exists(subquery);
        };
    }

    public Specification<Product> hasEligibleVariant() {
        return (root, query, cb) -> eligibleVariantExists(root, query, cb, null, null);
    }

    private static jakarta.persistence.criteria.Predicate activeOrMissing(
            CriteriaBuilder criteriaBuilder, Join<Category, Category> category) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(category.get("id")),
                criteriaBuilder.equal(category.get("status"), CategoryStatus.ACTIVE));
    }

    public Specification<Product> hasMatchingActiveVariant(Map<Long, List<Long>> optionValueIdsByOption) {
        if (optionValueIdsByOption == null || optionValueIdsByOption.isEmpty()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        List<Long> selectedValueIds =
                optionValueIdsByOption.values().stream().flatMap(List::stream).toList();
        int optionGroupCount = optionValueIdsByOption.size();
        return (root, query, criteriaBuilder) -> {
            var subquery = query.subquery(Long.class);
            var variant = subquery.from(ProductVariant.class);
            var selection = variant.join("selections");
            subquery.select(variant.get("id"));
            subquery.where(
                    criteriaBuilder.equal(variant.get("product").get("id"), root.get("id")),
                    criteriaBuilder.equal(variant.get("status"), ProductVariantStatus.ACTIVE),
                    eligibleVariantSelections(criteriaBuilder, query, variant),
                    selection.get("optionValueId").in(selectedValueIds));
            subquery.groupBy(variant.get("id"));
            subquery.having(criteriaBuilder.equal(
                    criteriaBuilder.countDistinct(selection.get("id").get("optionId")), (long) optionGroupCount));
            return criteriaBuilder.exists(subquery);
        };
    }

    @Override
    public Specification<Product> getSortSpec(String sort) {
        if (sort == null) return sortByDate(false);

        boolean asc = !sort.startsWith("-");
        String sortField = asc ? sort : sort.substring(1);
        return switch (sortField) {
            case FIELD_PRICE:
                yield sortByPrice(asc);
            case FIELD_NAME:
                yield sortByName(asc);
            case FIELD_DATE:
                yield sortByDate(asc);
            default:
                yield sortByDate(false);
        };
    }

    public Specification<Product> hasPriceInRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> eligibleVariantExists(root, query, cb, minPrice, maxPrice);
    }

    private jakarta.persistence.criteria.Predicate eligibleVariantExists(
            jakarta.persistence.criteria.Root<Product> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        var subquery = query.subquery(Long.class);
        var variant = subquery.from(ProductVariant.class);
        List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
        predicates.add(cb.equal(variant.get("product").get("id"), root.get("id")));
        predicates.add(cb.equal(variant.get("status"), ProductVariantStatus.ACTIVE));
        predicates.add(eligibleVariantSelections(cb, query, variant));
        if (minPrice != null) predicates.add(cb.greaterThanOrEqualTo(variant.get("basePrice"), minPrice));
        if (maxPrice != null) predicates.add(cb.lessThanOrEqualTo(variant.get("basePrice"), maxPrice));
        subquery.select(variant.get("id")).where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        return cb.exists(subquery);
    }

    private jakarta.persistence.criteria.Predicate eligibleVariantSelections(
            CriteriaBuilder cb,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.From<?, ProductVariant> variant) {
        var inactive = query.subquery(Long.class);
        var selection = inactive.from(ProductVariantSelection.class);
        inactive.select(selection.get("id"));
        inactive.where(cb.and(
                cb.equal(selection.get("variant"), variant),
                cb.or(
                        cb.notEqual(selection.get("optionValue").get("status"), VariantOptionStatus.ACTIVE),
                        cb.notEqual(
                                selection.get("optionValue").get("option").get("status"),
                                VariantOptionStatus.ACTIVE))));
        return cb.not(cb.exists(inactive));
    }

    public Specification<Product> hasPublished(Boolean hasPublished) {
        return (root, query, criteriaBuilder) -> {
            if (hasPublished == null) return criteriaBuilder.conjunction();
            return criteriaBuilder.equal(root.get("published"), hasPublished);
        };
    }

    public Specification<Product> isInStock(Boolean inStock) {
        return (root, query, criteriaBuilder) -> {
            if (inStock == null) return criteriaBuilder.conjunction();
            var subquery = query.subquery(Long.class);
            var balance = subquery.from(InventoryBalance.class);
            Join<InventoryBalance, ProductVariant> variant = balance.join("variant");
            Join<ProductVariant, Product> product = variant.join("product");
            Join<Product, Category> category = product.join("category");
            Join<Category, Category> parent = category.join("parent", JoinType.LEFT);
            Join<Category, Category> grandparent = parent.join("parent", JoinType.LEFT);
            Join<Category, Category> greatGrandparent = grandparent.join("parent", JoinType.LEFT);
            Join<Product, com.xdpsx.ecommerce.catalog.brand.domain.Brand> brand = product.join("brand");
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(criteriaBuilder.equal(product.get("id"), root.get("id")));
            predicates.add(criteriaBuilder.equal(variant.get("status"), ProductVariantStatus.ACTIVE));
            predicates.add(criteriaBuilder.isTrue(product.get("published")));
            predicates.add(criteriaBuilder.equal(brand.get("status"), BrandStatus.ACTIVE));
            predicates.add(criteriaBuilder.equal(category.get("status"), CategoryStatus.ACTIVE));
            predicates.add(activeOrMissing(criteriaBuilder, parent));
            predicates.add(activeOrMissing(criteriaBuilder, grandparent));
            predicates.add(criteriaBuilder.isNull(greatGrandparent.get("id")));
            predicates.add(eligibleVariantSelections(criteriaBuilder, query, variant));
            predicates.add(criteriaBuilder.greaterThan(balance.get("onHand"), balance.get("reserved")));
            subquery.select(balance.get("variantId"));
            subquery.where(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
            var available = criteriaBuilder.exists(subquery);
            return inStock ? available : criteriaBuilder.not(available);
        };
    }

    public Specification<Product> belongsToCategory(Integer categoryId) {
        return (root, query, criteriaBuilder) -> {
            if (categoryId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("category").get("id"), categoryId);
        };
    }

    public Specification<Product> belongsToBrand(Integer brandId) {
        return (root, query, criteriaBuilder) -> {
            if (brandId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("brand").get("id"), brandId);
        };
    }

    public Specification<Product> belongsToBrands(List<Integer> brandIds) {
        return (root, query, criteriaBuilder) -> {
            if (brandIds == null || brandIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            CriteriaBuilder.In<Integer> inClause =
                    criteriaBuilder.in(root.get("brand").get("id"));
            for (Integer id : brandIds) {
                inClause.value(id);
            }
            return inClause;
        };
    }

    public Specification<Product> sortByPrice(boolean asc) {
        return (root, query, criteriaBuilder) -> {
            var subquery = query.subquery(BigDecimal.class);
            var variant = subquery.from(ProductVariant.class);
            subquery.select(criteriaBuilder.min(variant.get("basePrice")));
            subquery.where(
                    criteriaBuilder.equal(variant.get("product").get("id"), root.get("id")),
                    criteriaBuilder.equal(variant.get("status"), ProductVariantStatus.ACTIVE),
                    eligibleVariantSelections(criteriaBuilder, query, variant));
            if (asc) {
                query.orderBy(criteriaBuilder.asc(subquery), criteriaBuilder.asc(root.get("id")));
            } else {
                query.orderBy(criteriaBuilder.desc(subquery), criteriaBuilder.asc(root.get("id")));
            }
            return criteriaBuilder.conjunction();
        };
    }
}
