package com.xdpsx.ecommerce.catalog.category.persistence;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.category.domain.Category;
import com.xdpsx.ecommerce.catalog.category.domain.CategoryStatus;
import com.xdpsx.ecommerce.catalog.shared.persistence.BaseSpecification;
import com.xdpsx.ecommerce.catalog.shared.persistence.SearchCriteria;
import com.xdpsx.ecommerce.catalog.shared.persistence.SearchOperator;

public class CategorySpecification extends BaseSpecification<Category> {
    private static CategorySpecification instance;

    public static CategorySpecification getInstance() {
        if (instance == null) {
            instance = new CategorySpecification();
        }
        return instance;
    }

    public Specification<Category> buildAdminCategoriesSpec(
            String name, CategoryStatus status, Integer parentId, String sort, Integer level) {
        List<SearchCriteria> criteriaList = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            criteriaList.add(new SearchCriteria("name", name, SearchOperator.LIKE));
        }

        if (status != null) {
            criteriaList.add(new SearchCriteria("status", status, SearchOperator.EQUAL));
        }

        Specification<Category> spec = build(criteriaList);

        // The admin response maps image.url on every row. image is an unqualified @OneToOne, hence eager by
        // default, so without a fetch Hibernate would resolve it with one secondary select per row.
        spec = spec.and(fetchImage());

        if (parentId != null) {
            // The association cannot be compared to a raw id through the generic
            // SearchCriteria path, which would
            // compare the Category entity to an Integer. The id of the association is
            // compared explicitly instead,
            // which also makes this an inner join and therefore returns direct children
            // only.
            spec = spec.and(parentIdEquals(parentId));
        }

        spec = applySort(spec, sort);

        if (level != null) {
            spec = spec.and(levelEquals(level));
        }

        return spec;
    }

    private Specification<Category> parentIdEquals(Integer parentId) {
        return (root, query, cb) -> cb.equal(root.get("parent").get("id"), parentId);
    }

    /** Fetch join must not be applied to the count query that pagination issues alongside the page query. */
    private Specification<Category> fetchImage() {
        return (root, query, cb) -> {
            if (query.getResultType().equals(Category.class)) {
                root.fetch("image", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }

    private Specification<Category> levelEquals(Integer level) {
        return (root, query, cb) -> {
            if (level == null) return null;
            root.fetch("parent", JoinType.LEFT);
            return switch (level) {
                case 1 -> cb.isNull(root.get("parent"));
                case 2 ->
                    cb.and(
                            cb.isNotNull(root.get("parent")),
                            cb.isNull(root.get("parent").get("parent")));
                case 3 ->
                    cb.and(
                            cb.isNotNull(root.get("parent")),
                            cb.isNotNull(root.get("parent").get("parent")),
                            cb.isNull(root.get("parent").get("parent").get("parent")));
                default -> throw new IllegalArgumentException("Only levels 1 to 3 are supported.");
            };
        };
    }
}
