package com.xdpsx.ecommerce.catalog.brand.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import com.xdpsx.ecommerce.catalog.brand.domain.Brand;
import com.xdpsx.ecommerce.catalog.brand.domain.BrandStatus;

@ExtendWith(MockitoExtension.class)
class BrandSpecificationTest {

    @Mock
    private Root<Brand> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Test
    void adminSpecification_ShouldFilterByStatusAndFetchOnlyToOneImage() {
        doReturn(Brand.class).when(query).getResultType();
        Specification<Brand> specification =
                BrandSpecification.getInstance().buildAdminBrandsSpec("Nike", BrandStatus.ACTIVE, "name");

        assertThat(specification).isNotNull();
        specification.toPredicate(root, query, criteriaBuilder);

        verify(root, atLeastOnce()).get("name");
        verify(root, atLeastOnce()).get("status");
        verify(root).fetch("image", JoinType.LEFT);
        verify(root, never()).fetch("categories", JoinType.LEFT);
    }

    @Test
    void adminSpecification_ShouldNotFetchImageOnCountQuery() {
        doReturn(Long.class).when(query).getResultType();

        BrandSpecification.getInstance()
                .buildAdminBrandsSpec(null, null, null)
                .toPredicate(root, query, criteriaBuilder);

        verify(root, never()).fetch("image", JoinType.LEFT);
        verify(root, never()).fetch("categories", JoinType.LEFT);
    }
}
