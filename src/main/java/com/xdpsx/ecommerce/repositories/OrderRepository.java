package com.xdpsx.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.xdpsx.ecommerce.entities.Order;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId "
            + "ORDER BY CASE WHEN o.updatedAt IS NOT NULL THEN o.updatedAt ELSE o.createdAt END DESC")
    Page<Order> findByUser(Long userId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findById(Long id);

    @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.user.id = :userId AND o.trackingNumber = :trackingNumber")
    Optional<Order> findByUserIdAndTrackingNumber(Long userId, String trackingNumber);
}
