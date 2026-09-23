package com.xdpsx.ecommerce.catalog.product.persistence;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.shared.persistence.BasicSpecification;

@Component
public class ProductSpecification extends BasicSpecification<Product> {

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
        return Specification.where(hasName(name))
                .and(getSortSpec(sort))
                .and(hasPublished(hasPublished))
                .and(hasMinPrice(minPrice))
                .and(hasMaxPrice(maxPrice))
                .and(hasDiscount(hasDiscount))
                .and(isInStock(inStock))
                .and(belongsToCategory(categoryId))
                .and(belongsToBrand(brandId));
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
