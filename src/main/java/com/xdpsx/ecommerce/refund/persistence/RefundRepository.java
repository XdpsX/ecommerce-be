package com.xdpsx.ecommerce.refund.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.refund.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByPaymentId(Long paymentId);

    @Query("SELECT r.id AS refundId, p.id AS paymentId, o.id AS orderId "
            + "FROM Refund r JOIN r.payment p JOIN p.order o WHERE r.id = :id")
    Optional<RefundLockTarget> findLockTargetById(@Param("id") Long id);

    @Query("SELECT r FROM Refund r JOIN FETCH r.payment p JOIN FETCH p.order WHERE r.id = :id")
    Optional<Refund> findByIdWithPaymentAndOrder(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r JOIN FETCH r.payment p JOIN FETCH p.order WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r JOIN FETCH r.payment WHERE r.payment.id = :paymentId")
    Optional<Refund> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);
}
