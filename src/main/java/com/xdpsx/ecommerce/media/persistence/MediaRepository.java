package com.xdpsx.ecommerce.media.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaStatus;

public interface MediaRepository extends CrudRepository<Media, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id IN :ids ORDER BY m.id")
    List<Media> findAllByIdInForUpdate(@Param("ids") Collection<String> ids);

    @Query("SELECT m FROM Media m WHERE m.status = :status")
    List<Media> findAllByStatus(@Param("status") MediaStatus status);

    @Query(
            """
		SELECT m FROM Media m
		WHERE m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY
			AND m.createdAt < :expiryTime
		""")
    List<Media> findExpiredTemporaryMedia(@Param("expiryTime") LocalDateTime expiryTime);

    /**
     * Atomically claims a temporary Media for deletion. The update is deliberately short-lived so the storage
     * provider is never called while a database lock is held.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Media m SET m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.PENDING_DELETE "
            + "WHERE m.id = :id AND m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY")
    int claimTemporaryForDeletion(@Param("id") String id);

    /** Claims an expired temporary Media only if it is still temporary and still expired. */
    @Modifying
    @Transactional
    @Query("UPDATE Media m SET m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.PENDING_DELETE "
            + "WHERE m.id = :id "
            + "AND m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY "
            + "AND m.createdAt < :expiryTime")
    int claimExpiredTemporaryForDeletion(@Param("id") String id, @Param("expiryTime") LocalDateTime expiryTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
		SELECT m FROM Media m
		WHERE m.id = :id
			AND m.status = com.xdpsx.ecommerce.media.domain.MediaStatus.TEMPORARY
			AND m.purpose = :purpose
	""")
    Optional<Media> findAttachableById(@Param("id") String id, @Param("purpose") MediaPurpose purpose);

    @Query("""
		SELECT m FROM Media m
		WHERE m.id = :id AND m.status = :status
	""")
    Optional<Media> findByIdAndStatus(@Param("id") String id, @Param("status") MediaStatus status);
}
