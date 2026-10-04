package com.xdpsx.ecommerce.order.persistence;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.order.domain.Order;

@ExtendWith(MockitoExtension.class)
class OrderSpecificationTest {
    @Mock
    private Root<Order> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private Predicate conjunction;

    @Mock
    private Predicate trackingPredicate;

    @Mock
    private Predicate combinedPredicate;

    @Mock
    private Path<String> trackingNumberPath;

    @Test
    void trackingNumberFilter_ShouldTrimAndMatchExactly() {
        when(criteriaBuilder.conjunction()).thenReturn(conjunction);
        when(root.<String>get("trackingNumber")).thenReturn(trackingNumberPath);
        when(criteriaBuilder.equal(trackingNumberPath, "TRK-42")).thenReturn(trackingPredicate);
        when(criteriaBuilder.and(conjunction, trackingPredicate)).thenReturn(combinedPredicate);

        Predicate result = OrderSpecification.withStatusAndPaymentStatus(null, null, " TRK-42 ")
                .toPredicate(root, query, criteriaBuilder);

        assertSame(combinedPredicate, result);
        verify(criteriaBuilder).equal(trackingNumberPath, "TRK-42");
    }

    @Test
    void blankTrackingNumber_ShouldNotAddAFilter() {
        when(criteriaBuilder.conjunction()).thenReturn(conjunction);

        Predicate result = OrderSpecification.withStatusAndPaymentStatus(null, null, "  ")
                .toPredicate(root, query, criteriaBuilder);

        assertSame(conjunction, result);
        verify(root, never()).<String>get("trackingNumber");
    }
}
