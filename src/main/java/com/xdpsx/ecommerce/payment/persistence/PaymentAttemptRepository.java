package com.xdpsx.ecommerce.payment.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.xdpsx.ecommerce.payment.domain.PaymentAttempt;
import com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttempt a WHERE a.payment.id = :paymentId "
            + "AND a.status = com.xdpsx.ecommerce.payment.domain.PaymentAttemptStatus.PENDING "
            + "ORDER BY a.id")
    List<PaymentAttempt> findPendingByPaymentIdForUpdate(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentAttempt> findTopByPaymentIdAndStatusOrderByCreatedAtDesc(
            Long paymentId, PaymentAttemptStatus status);

    @Query("SELECT a.payment.order.id FROM PaymentAttempt a WHERE a.providerReference = :providerReference")
    Optional<Long> findOrderIdByProviderReference(String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PaymentAttempt a JOIN FETCH a.payment p JOIN FETCH p.order "
            + "WHERE a.providerReference = :providerReference")
    Optional<PaymentAttempt> findByProviderReferenceForUpdate(String providerReference);
}
