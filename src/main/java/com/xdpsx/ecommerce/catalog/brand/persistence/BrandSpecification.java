package com.xdpsx.ecommerce.catalog.brand.persistence;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;
import com.xdpsx.ecommerce.catalog.shared.persistence.BaseSpecification;
import com.xdpsx.ecommerce.catalog.shared.persistence.SearchCriteria;
import com.xdpsx.ecommerce.catalog.shared.persistence.SearchOperator;

public class BrandSpecification extends BaseSpecification<Brand> {
    private static BrandSpecification instance;

    public static BrandSpecification getInstance() {
        if (instance == null) {
            instance = new BrandSpecification();
        }
        return instance;
    }

    public Specification<Brand> buildAdminBrandsSpec(String name, BrandStatus status, String sort) {
        List<SearchCriteria> criteriaList = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            criteriaList.add(new SearchCriteria("name", name, SearchOperator.LIKE));
        }

        if (status != null) {
            criteriaList.add(new SearchCriteria("status", status, SearchOperator.EQUAL));
        }

        Specification<Brand> spec = build(criteriaList);

        // The admin response maps image on every row. image is an unqualified @OneToOne, hence eager by
        // default, so without a fetch Hibernate would resolve it with one secondary select per row. The
        // categories collection is deliberately NOT fetched here: a collection fetch join on a pageable
        // query paginates in memory, duplicates rows and breaks the count query. Categories are batch-fetched
        // for the resolved page in the service instead.
        spec = spec.and(fetchImage());

        spec = applySort(spec, sort);

        return spec;
    }

    /** Fetch join must not be applied to the count query that pagination issues alongside the page query. */
    private Specification<Brand> fetchImage() {
        return (root, query, cb) -> {
            if (query.getResultType().equals(Brand.class)) {
                root.fetch("image", JoinType.LEFT);
            }
            return cb.conjunction();
        };
    }
}
