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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r JOIN FETCH r.payment WHERE r.payment.id = :paymentId")
    Optional<Refund> findByPaymentIdForUpdate(@Param("paymentId") Long paymentId);
}
