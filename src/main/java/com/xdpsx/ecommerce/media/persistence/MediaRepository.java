package com.xdpsx.ecommerce.media.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public interface MediaRepository extends CrudRepository<Media, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.id IN :ids ORDER BY m.id")
    List<Media> findAllByIdInForUpdate(@Param("ids") Collection<String> ids);

    @Query("""
            SELECT m FROM Media m
            WHERE m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.PENDING_DELETE
              AND (m.cleanupNextAttemptAt IS NULL OR m.cleanupNextAttemptAt <= :now)
              AND (m.uploadAuthorizationExpiresAt IS NULL OR m.uploadAuthorizationExpiresAt <= :replayBoundary)
            ORDER BY COALESCE(m.cleanupNextAttemptAt, COALESCE(m.uploadedAt, m.createdAt)), m.id
            """)
    List<Media> findPendingDeletionCleanupCandidates(
            @Param("now") LocalDateTime now, @Param("replayBoundary") LocalDateTime replayBoundary, Pageable pageable);

    @Query("""
            SELECT m FROM Media m
            WHERE m.attachmentStatus = com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus.TEMPORARY
              AND m.uploadStatus = com.xdpsx.ecommerce.media.domain.MediaUploadStatus.UPLOADED
              AND m.attachmentOwnerType IS NULL AND m.attachmentOwnerId IS NULL
              AND COALESCE(m.uploadedAt, m.createdAt) <= :temporaryExpiry
              AND (m.uploadAuthorizationExpiresAt IS NULL OR m.uploadAuthorizationExpiresAt <= :replayBoundary)
            ORDER BY COALESCE(m.cleanupNextAttemptAt, COALESCE(m.uploadedAt, m.createdAt)), m.id
            """)
    List<Media> findExpiredTemporaryCleanupCandidates(
            @Param("temporaryExpiry") LocalDateTime temporaryExpiry,
            @Param("replayBoundary") LocalDateTime replayBoundary,
            Pageable pageable);

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
                    + "AND COALESCE(m.uploadedAt, m.createdAt) <= :expiryTime "
                    + "AND (m.uploadAuthorizationExpiresAt IS NULL OR m.uploadAuthorizationExpiresAt <= :replayBoundary) "
                    + "AND m.attachmentOwnerType IS NULL AND m.attachmentOwnerId IS NULL")
    int claimExpiredTemporaryForDeletion(
            @Param("id") String id,
            @Param("expiryTime") LocalDateTime expiryTime,
            @Param("replayBoundary") LocalDateTime replayBoundary);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Media m WHERE m.externalId = :publicId")
    Optional<Media> findByExternalIdForUpdate(@Param("publicId") String publicId);

    @Query(value = """
            SELECT * FROM media
            WHERE upload_status = 'PENDING_UPLOAD'
              AND attachment_status = 'TEMPORARY'
              AND upload_authorization_issued_at IS NOT NULL
              AND upload_authorization_expires_at IS NOT NULL
              AND upload_authorization_expires_at <= :initialDueCutoff
              AND (reconciliation_next_attempt_at IS NULL OR reconciliation_next_attempt_at <= :now)
            ORDER BY COALESCE(reconciliation_next_attempt_at,
                              DATE_ADD(upload_authorization_expires_at, INTERVAL :completionGraceSeconds SECOND)), id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Media> findPendingUploadReconciliationCandidates(
            @Param("initialDueCutoff") LocalDateTime initialDueCutoff,
            @Param("now") LocalDateTime now,
            @Param("completionGraceSeconds") long completionGraceSeconds,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM media
            WHERE upload_status = 'UPLOADED'
              AND processing_status = 'PROCESSING'
              AND attachment_status IN ('TEMPORARY', 'ACTIVE')
              AND COALESCE(uploaded_at, created_at) <= :initialDueCutoff
              AND COALESCE(uploaded_at, created_at) >= :horizonCutoff
              AND (reconciliation_next_attempt_at IS NULL OR reconciliation_next_attempt_at <= :now)
            ORDER BY COALESCE(reconciliation_next_attempt_at,
                              DATE_ADD(COALESCE(uploaded_at, created_at), INTERVAL :processingTimeoutSeconds SECOND)), id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Media> findProcessingReconciliationCandidates(
            @Param("initialDueCutoff") LocalDateTime initialDueCutoff,
            @Param("horizonCutoff") LocalDateTime horizonCutoff,
            @Param("now") LocalDateTime now,
            @Param("processingTimeoutSeconds") long processingTimeoutSeconds,
            @Param("batchSize") int batchSize);

    @Query(value = """
            SELECT * FROM media
            WHERE upload_status = 'UPLOADED'
              AND processing_status = 'PROCESSING'
              AND attachment_status IN ('TEMPORARY', 'ACTIVE')
              AND COALESCE(uploaded_at, created_at) < :horizonCutoff
            ORDER BY COALESCE(uploaded_at, created_at), id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Media> findProcessingPastRecoveryHorizon(
            @Param("horizonCutoff") LocalDateTime horizonCutoff, @Param("batchSize") int batchSize);

    @Modifying
    @Transactional
    @Query("UPDATE Media m SET m.processingReference = :batchId WHERE m.id = :id AND m.processingReference IS NULL")
    int bindProcessingReferenceIfAbsent(@Param("id") String id, @Param("batchId") String batchId);
}
