package com.xdpsx.ecommerce.order.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.xdpsx.ecommerce.order.domain.Order;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Query("SELECT o FROM Order o WHERE o.user.id = :userId "
            + "ORDER BY CASE WHEN o.updatedAt IS NOT NULL THEN o.updatedAt ELSE o.createdAt END DESC")
    Page<Order> findByUser(Long userId, Pageable pageable);

    @Query(
            "SELECT DISTINCT o FROM Order o JOIN FETCH o.items LEFT JOIN FETCH o.payment p LEFT JOIN FETCH p.refund WHERE o.id = :id")
    Optional<Order> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.payment p LEFT JOIN FETCH p.refund WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT o FROM Order o LEFT JOIN FETCH o.payment p LEFT JOIN FETCH p.refund WHERE o.id = :orderId AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(Long orderId, Long userId);

    @Query(
            "SELECT DISTINCT o FROM Order o JOIN FETCH o.items LEFT JOIN FETCH o.payment p LEFT JOIN FETCH p.refund WHERE o.user.id = :userId AND o.trackingNumber = :trackingNumber")
    Optional<Order> findByUserIdAndTrackingNumber(Long userId, String trackingNumber);

    @Query("SELECT o.id FROM Order o "
            + "WHERE o.status = com.xdpsx.ecommerce.order.domain.OrderStatus.PENDING_PAYMENT "
            + "AND o.reservationExpiresAt < :cutoff "
            + "AND o.id > :afterId ORDER BY o.id")
    List<Long> findExpiredPendingIds(
            @org.springframework.data.repository.query.Param("cutoff") Instant cutoff,
            @org.springframework.data.repository.query.Param("afterId") Long afterId,
            Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items LEFT JOIN FETCH o.payment "
            + "WHERE o.user.id = :userId AND o.idempotencyKeyHash = :keyHash")
    Optional<Order> findByUserIdAndIdempotencyKeyHash(Long userId, byte[] keyHash);
}
