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
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public interface MediaRepository extends CrudRepository<Media, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id IN :ids ORDER BY m.id")
    List<Media> findAllByIdInForUpdate(@Param("ids") Collection<String> ids);

    @Query("SELECT m FROM Media m WHERE m.attachmentStatus = :status AND "
            + "(m.uploadAuthorizationExpiresAt IS NULL OR m.uploadAuthorizationExpiresAt <= :replayBoundary)")
    List<Media> findAllByAttachmentStatusBeforeReplayBoundary(
            @Param("status") MediaAttachmentStatus status, @Param("replayBoundary") LocalDateTime replayBoundary);

    @Query("""
		SELECT m FROM Media m
		WHERE m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY
			AND m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.UPLOADED
			AND m.createdAt < :expiryTime
			AND (m.uploadAuthorizationExpiresAt IS NULL OR m.uploadAuthorizationExpiresAt <= :replayBoundary)
		""")
    List<Media> findExpiredTemporaryMedia(
            @Param("expiryTime") LocalDateTime expiryTime, @Param("replayBoundary") LocalDateTime replayBoundary);

    /**
     * Atomically claims a temporary Media for deletion. The update is deliberately short-lived so the storage
     * provider is never called while a database lock is held.
     */
    @Modifying
    @Transactional
    @Query(
            "UPDATE Media m SET m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.PENDING_DELETE "
                    + "WHERE m.id = :id "
                    + "AND m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY "
                    + "AND (m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.UPLOADED OR "
                    + "(m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.PENDING_UPLOAD "
                    + "AND m.uploadAuthorizationIssuedAt IS NOT NULL)) "
                    + "AND m.uploadedByUserId = :uploadedByUserId")
    int claimTemporaryForDeletion(@Param("id") String id, @Param("uploadedByUserId") Long uploadedByUserId);

    /** Claims an expired temporary Media only if it is still temporary and still expired. */
    @Modifying
    @Transactional
    @Query(
            "UPDATE Media m SET m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.PENDING_DELETE "
                    + "WHERE m.id = :id "
                    + "AND m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY "
                    + "AND m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.UPLOADED "
                    + "AND m.createdAt < :expiryTime")
    int claimExpiredTemporaryForDeletion(@Param("id") String id, @Param("expiryTime") LocalDateTime expiryTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
		SELECT m FROM Media m
		WHERE m.id = :id
			AND m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY
			AND m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.UPLOADED
			AND m.purpose = :purpose
			AND m.uploadedByUserId = :uploadedByUserId
	""")
    Optional<Media> findAttachableById(
            @Param("id") String id,
            @Param("purpose") MediaPurpose purpose,
            @Param("uploadedByUserId") Long uploadedByUserId);

    @Query("SELECT m FROM Media m WHERE m.id = :id AND m.uploadedByUserId = :uploaderId")
    Optional<Media> findOwnedById(@Param("id") String id, @Param("uploaderId") Long uploaderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id = :id AND m.uploadedByUserId = :uploaderId")
    Optional<Media> findOwnedByIdForUpdate(@Param("id") String id, @Param("uploaderId") Long uploaderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id = :id")
    Optional<Media> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.processingReference = :reference")
    Optional<Media> findByProcessingReferenceForUpdate(@Param("reference") String reference);
}
