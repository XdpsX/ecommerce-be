package com.xdpsx.ecommerce.auth.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xdpsx.ecommerce.auth.domain.RefreshSession;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT session FROM RefreshSession session WHERE session.id = :id")
    Optional<RefreshSession> findByIdForUpdate(@Param("id") String id);
}
