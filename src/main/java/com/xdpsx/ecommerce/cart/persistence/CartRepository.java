package com.xdpsx.ecommerce.cart.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.cart.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c WHERE c.id = :cartId")
    Optional<Cart> findByIdForUpdate(@Param("cartId") Long cartId);

    @Query("SELECT c.id FROM Cart c WHERE c.user IS NULL AND c.guestExpiresAt < :now ORDER BY c.id")
    List<Long> findExpiredGuestIds(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Cart c WHERE c.id IN :ids AND c.user IS NULL AND c.guestExpiresAt < :cutoff")
    int deleteExpiredGuestsIfStillExpired(@Param("ids") List<Long> ids, @Param("cutoff") Instant cutoff);
}
