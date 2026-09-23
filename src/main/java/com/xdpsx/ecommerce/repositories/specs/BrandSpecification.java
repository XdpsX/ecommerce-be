package com.xdpsx.ecommerce.repositories.specs;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.entities.Brand;
import com.xdpsx.ecommerce.repositories.common.SearchCriteria;
import com.xdpsx.ecommerce.repositories.common.SearchOperator;

public class BrandSpecification extends BaseSpecification<Brand> {
    private static BrandSpecification instance;

    public static BrandSpecification getInstance() {
        if (instance == null) {
            instance = new BrandSpecification();
        }
        return instance;
    }

    public Specification<Brand> buildAdminBrandsSpec(String name, Boolean publicFlg, String sort) {
        List<SearchCriteria> criteriaList = new ArrayList<>();

        if (name != null && !name.isBlank()) {
            criteriaList.add(new SearchCriteria("name", name, SearchOperator.LIKE));
        }

        if (publicFlg != null) {
            criteriaList.add(new SearchCriteria("publicFlg", publicFlg, SearchOperator.EQUAL));
        }

        Specification<Brand> spec = build(criteriaList);

        spec = applySort(spec, sort);

        spec = spec.and((root, query, builder) -> {
            root.fetch("categories", JoinType.LEFT);
            return builder.conjunction();
        });

        return spec;
    }
}
