package com.xdpsx.ecommerce.catalog.brand.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.persistence.criteria.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;

class BrandSpecificationTest {

    private BrandSpecification brandSpecification;

    private Root<Brand> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;

    @BeforeEach
    void setUp() {
        brandSpecification = BrandSpecification.getInstance();
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class);
    }

    @Test
    void testBuildAdminBrandsSpec_withNameAndPublicFlg() {
        String name = "Nike";
        Boolean publicFlg = true;
        String sort = "name";

        Specification<Brand> spec = brandSpecification.buildAdminBrandsSpec(name, publicFlg, sort);

        assertThat(spec).isNotNull();

        spec.toPredicate(root, query, cb);

        verify(root, atLeastOnce()).get("name");
        verify(root, atLeastOnce()).get("publicFlg");
        verify(root, atLeastOnce()).fetch("categories", JoinType.LEFT);
    }

    @Test
    void testBuildAdminBrandsSpec_withNameOnly() {
        String name = "Adidas";
        Boolean publicFlg = null;
        String sort = "-date";

        Specification<Brand> spec = brandSpecification.buildAdminBrandsSpec(name, publicFlg, sort);

        assertThat(spec).isNotNull();

        spec.toPredicate(root, query, cb);

        verify(root, atLeastOnce()).get("name");
        verify(root, never()).get("publicFlg");
        verify(root, atLeastOnce()).fetch("categories", JoinType.LEFT);
    }

    @Test
    void testBuildAdminBrandsSpec_withPublicFlgOnly() {
        String name = null;
        Boolean publicFlg = false;
        String sort = null;

        Specification<Brand> spec = brandSpecification.buildAdminBrandsSpec(name, publicFlg, sort);

        assertThat(spec).isNotNull();

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, atLeastOnce()).get("publicFlg");
        verify(root, atLeastOnce()).fetch("categories", JoinType.LEFT);
    }

    @Test
    void testBuildAdminBrandsSpec_withNoFilters() {
        String name = null;
        Boolean publicFlg = null;
        String sort = null;

        Specification<Brand> spec = brandSpecification.buildAdminBrandsSpec(name, publicFlg, sort);

        assertThat(spec).isNotNull();

        spec.toPredicate(root, query, cb);

        verify(root, never()).get(anyString());
        verify(root, atLeastOnce()).fetch("categories", JoinType.LEFT);
    }

    @Test
    void testBuildAdminBrandsSpec_withBlankName() {
        String name = " ";
        Boolean publicFlg = true;
        String sort = "date";

        Specification<Brand> spec = brandSpecification.buildAdminBrandsSpec(name, publicFlg, sort);

        assertThat(spec).isNotNull();

        spec.toPredicate(root, query, cb);

        verify(root, never()).get("name");
        verify(root, atLeastOnce()).get("publicFlg");
        verify(root, atLeastOnce()).fetch("categories", JoinType.LEFT);
    }
}
