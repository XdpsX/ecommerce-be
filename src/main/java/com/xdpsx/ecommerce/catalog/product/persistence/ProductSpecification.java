package com.xdpsx.ecommerce.catalog.product.persistence;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

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
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariantStatus;
import com.xdpsx.ecommerce.catalog.shared.persistence.BasicSpecification;

@Component
public class ProductSpecification extends BasicSpecification<Product> {

    public Specification<Product> getAdminFiltersSpec(String search, String sort, Boolean hasPublished) {
        return hasName(search).and(getSortSpec(sort)).and(hasPublished(hasPublished));
    }

    public Specification<Product> getStorefrontFiltersSpec(
            String search,
            String sort,
            Double minPrice,
            Double maxPrice,
            Boolean hasDiscount,
            Boolean inStock,
            Integer categoryId,
            Integer brandId,
            Map<Long, List<Long>> optionValueIdsByOption) {
        return storefrontVisibility()
                .and(hasName(search))
                .and(getSortSpec(sort))
                .and(hasMinPrice(minPrice))
                .and(hasMaxPrice(maxPrice))
                .and(hasDiscount(hasDiscount))
                .and(isInStock(inStock))
                .and(belongsToCategory(categoryId))
                .and(belongsToBrand(brandId))
                .and(hasMatchingActiveVariant(optionValueIdsByOption));
    }

    public Specification<Product> storefrontVisibility() {
        return hasPublished(true)
                .and(hasActiveBrand())
                .and(hasEffectivelyActiveCategory())
                .and(hasActiveVariant());
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

    public Specification<Product> getFiltersSpec(
            String name,
            String sort,
            Boolean hasPublished,
            Double minPrice,
            Double maxPrice,
            Boolean hasDiscount,
            Boolean inStock,
            Integer categoryId,
            Integer brandId) {
        return hasName(name)
                .and(getSortSpec(sort))
                .and(hasPublished(hasPublished))
                .and(hasMinPrice(minPrice))
                .and(hasMaxPrice(maxPrice))
                .and(hasDiscount(hasDiscount))
                .and(isInStock(inStock))
                .and(belongsToCategory(categoryId))
                .and(belongsToBrand(brandId));
    }

    private static jakarta.persistence.criteria.Predicate activeOrMissing(
            CriteriaBuilder criteriaBuilder, Join<Category, Category> category) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(category.get("id")),
                criteriaBuilder.equal(category.get("status"), CategoryStatus.ACTIVE));
    }

    public Specification<Product> getFiltersSpec(
            String name,
            String sort,
            Boolean hasPublished,
            Double minPrice,
            Double maxPrice,
            Boolean hasDiscount,
            Boolean inStock,
            Integer categoryId,
            Integer brandId,
            Map<Long, List<Long>> optionValueIdsByOption) {
        return getFiltersSpec(name, sort, hasPublished, minPrice, maxPrice, hasDiscount, inStock, categoryId, brandId)
                .and(hasMatchingActiveVariant(optionValueIdsByOption));
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

    public Specification<Product> hasMinPrice(Double minPrice) {
        return (root, query, criteriaBuilder) -> {
            if (minPrice == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
        };
    }

    public Specification<Product> hasMaxPrice(Double maxPrice) {
        return (root, query, criteriaBuilder) -> {
            if (maxPrice == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
        };
    }

    public Specification<Product> hasPublished(Boolean hasPublished) {
        return (root, query, criteriaBuilder) -> {
            if (hasPublished == null) return criteriaBuilder.conjunction();
            return criteriaBuilder.equal(root.get("published"), hasPublished);
        };
    }

    public Specification<Product> hasDiscount(Boolean hasDiscount) {
        return (root, query, criteriaBuilder) -> {
            if (hasDiscount == null) return criteriaBuilder.conjunction();
            if (hasDiscount) {
                return criteriaBuilder.greaterThan(root.get("discountPercent"), 0);
            } else {
                return criteriaBuilder.equal(root.get("discountPercent"), 0);
            }
        };
    }

    public Specification<Product> isInStock(Boolean inStock) {
        return (root, query, criteriaBuilder) -> {
            if (inStock == null) return criteriaBuilder.conjunction();
            return criteriaBuilder.equal(root.get("inStock"), inStock);
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
            if (asc) {
                query.orderBy(criteriaBuilder.asc(root.get("price")));
            } else {
                query.orderBy(criteriaBuilder.desc(root.get("price")));
            }
            return criteriaBuilder.conjunction();
        };
    }
}
