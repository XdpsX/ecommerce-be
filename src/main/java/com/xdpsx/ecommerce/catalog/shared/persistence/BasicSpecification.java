package com.xdpsx.ecommerce.catalog.shared.persistence;

import static com.xdpsx.ecommerce.catalog.shared.persistence.FieldConstants.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class BasicSpecification<T> {
    public Specification<T> getFiltersSpec(String name, String sort) {
        return Specification.where(hasName(name)).and(getSortSpec(sort));
    }

    public Specification<T> getSortSpec(String sort) {
        if (sort == null) return sortByDate(false);

        boolean asc = !sort.startsWith("-");
        String sortField = asc ? sort : sort.substring(1);
        return switch (sortField) {
            case FIELD_NAME:
                yield sortByName(asc);
            case FIELD_DATE:
                yield sortByDate(asc);
            default:
                yield sortByDate(false);
        };
    }

    protected Specification<T> hasName(String name) {
        return (Root<T> root, CriteriaQuery<?> query, CriteriaBuilder builder) -> {
            if (name == null || name.isEmpty()) {
                return builder.conjunction();
            }
            return builder.like(builder.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    protected Specification<T> sortByDate(boolean asc) {
        return (root, query, criteriaBuilder) -> {
            if (asc) {
                query.orderBy(
                        criteriaBuilder.asc(criteriaBuilder.coalesce(root.get("updatedAt"), root.get("createdAt"))));
            } else {
                query.orderBy(
                        criteriaBuilder.desc(criteriaBuilder.coalesce(root.get("updatedAt"), root.get("createdAt"))));
            }
            return criteriaBuilder.conjunction();
        };
    }

    protected Specification<T> sortByName(boolean asc) {
        return (root, query, criteriaBuilder) -> {
            if (asc) {
                query.orderBy(criteriaBuilder.asc(root.get("name")));
            } else {
                query.orderBy(criteriaBuilder.desc(root.get("name")));
            }
            return criteriaBuilder.conjunction();
        };
    }
}
