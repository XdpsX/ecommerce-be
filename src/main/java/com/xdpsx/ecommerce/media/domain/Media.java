package com.xdpsx.ecommerce.media.domain;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.xdpsx.ecommerce.common.persistence.AuditEntity;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "media")
@EntityListeners(AuditingEntityListener.class)
public class Media extends AuditEntity {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(length = 255, nullable = false, unique = true)
    @Setter(AccessLevel.NONE)
    private String externalId;

    @Column(nullable = true)
    private String url;

    @Column(length = 100)
    private String caption;

    @Column(length = 30, nullable = true)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    @Setter(AccessLevel.NONE)
    private MediaPurpose purpose;

    @Setter(AccessLevel.NONE)
    @Column(name = "uploaded_by_user_id", updatable = false)
    private Long uploadedByUserId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", length = 32, nullable = false)
    @Setter(AccessLevel.NONE)
    private MediaUploadStatus uploadStatus = MediaUploadStatus.UPLOADED;

    @Column(name = "intent_expires_at")
    @Setter(AccessLevel.NONE)
    private LocalDateTime intentExpiresAt;

    @Column(name = "expected_content_type", length = 100)
    @Setter(AccessLevel.NONE)
    private String expectedContentType;

    @Column(name = "expected_size_bytes")
    @Setter(AccessLevel.NONE)
    private Long expectedSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_status", length = 32, nullable = false)
    private MediaAttachmentStatus attachmentStatus;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", length = 32, nullable = false)
    private MediaProcessingStatus processingStatus = MediaProcessingStatus.READY;

    @Column(name = "processing_failure_reason", length = 500)
    private String processingFailureReason;

    @Column(name = "processing_reference", length = 255, unique = true)
    private String processingReference;

    public static Media createPendingUpload(
            String id,
            String publicId,
            Long uploadedByUserId,
            MediaPurpose purpose,
            String expectedContentType,
            long expectedSizeBytes,
            LocalDateTime intentExpiresAt) {
        Media media = Media.builder()
                .id(id)
                .externalId(publicId)
                .uploadedByUserId(uploadedByUserId)
                .purpose(purpose)
                .uploadStatus(MediaUploadStatus.PENDING_UPLOAD)
                .processingStatus(MediaProcessingStatus.PENDING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .intentExpiresAt(intentExpiresAt)
                .expectedContentType(expectedContentType)
                .expectedSizeBytes(expectedSizeBytes)
                .build();
        media.validateState();
        return media;
    }

    public static Media createUploaded(
            String id,
            String externalId,
            String url,
            String caption,
            String contentType,
            MediaPurpose purpose,
            Long uploadedByUserId,
            MediaAttachmentStatus attachmentStatus,
            String processingReference) {
        Media media = Media.builder()
                .id(id)
                .externalId(externalId)
                .url(url)
                .caption(caption)
                .contentType(contentType)
                .purpose(purpose)
                .uploadedByUserId(uploadedByUserId)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .attachmentStatus(attachmentStatus)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .processingReference(processingReference)
                .build();
        media.validateState();
        return media;
    }

    @PrePersist
    @PreUpdate
    private void validateBeforePersistence() {
        validateState();
    }

    /** Enforces lifecycle invariants independently of persistence callbacks. */
    public void validateState() {
        if (uploadStatus == null || processingStatus == null || attachmentStatus == null || purpose == null) {
            throw new IllegalStateException("Media lifecycle state and purpose must be set");
        }
        if (uploadStatus == MediaUploadStatus.PENDING_UPLOAD) {
            if (attachmentStatus == MediaAttachmentStatus.ACTIVE) {
                throw new IllegalStateException("Pending uploads cannot be active");
            }
            if (intentExpiresAt == null || expectedContentType == null || expectedContentType.isBlank()) {
                throw new IllegalStateException("Pending uploads require expected content type and expiry");
            }
            if (expectedSizeBytes == null || expectedSizeBytes <= 0) {
                throw new IllegalStateException("Pending uploads require a positive expected size");
            }
            if (url != null || contentType != null) {
                throw new IllegalStateException("Pending uploads cannot contain an unverified asset URL or type");
            }
        } else {
            if (processingStatus == MediaProcessingStatus.PENDING) {
                throw new IllegalStateException("Uploaded media cannot have pending processing status");
            }
            if (url == null || url.isBlank() || contentType == null || contentType.isBlank()) {
                throw new IllegalStateException("Uploaded media requires URL and content type");
            }
        }
    }

    /**
     * Attaches this Media to an aggregate. Only a valid, uploaded temporary asset may be activated.
     *
     * @throws IllegalStateException when the Media lifecycle is invalid or is not {@link MediaAttachmentStatus#TEMPORARY}
     */
    public void activate() {
        validateState();
        if (uploadStatus != MediaUploadStatus.UPLOADED || processingStatus == MediaProcessingStatus.PENDING) {
            throw new IllegalStateException("Only uploaded and verified media can be activated");
        }
        if (attachmentStatus != MediaAttachmentStatus.TEMPORARY) {
            throw new IllegalStateException(
                    "Only temporary media can be activated, current status: " + attachmentStatus);
        }
        attachmentStatus = MediaAttachmentStatus.ACTIVE;
        validateState();
    }

    /**
     * Requests removal of the stored asset. Idempotent for {@link MediaAttachmentStatus#PENDING_DELETE}; the
     * physical asset is removed later by the cleanup process.
     *
     * @throws IllegalStateException when the status has not been loaded
     */
    public void markPendingDeletion() {
        if (attachmentStatus == null) {
            throw new IllegalStateException("Media attachment status is not set");
        }
        attachmentStatus = MediaAttachmentStatus.PENDING_DELETE;
    }

    /** Marks all eager transformations as ready. Repeated success notifications are no-ops. */
    public void markProcessingReady() {
        if (processingStatus == MediaProcessingStatus.PROCESSING) {
            processingStatus = MediaProcessingStatus.READY;
            processingFailureReason = null;
        }
    }

    /** Marks eager processing as failed with a bounded, log-safe reason. */
    public void markProcessingFailed(String reason) {
        if (processingStatus != MediaProcessingStatus.PROCESSING) {
            return;
        }
        processingStatus = MediaProcessingStatus.FAILED;
        processingFailureReason = sanitizeFailureReason(reason);
    }

    private static String sanitizeFailureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Cloudinary eager processing failed";
        }
        String sanitized = reason.replaceAll("[\\p{Cntrl}&&[^\\r\\n]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isEmpty()) {
            return "Cloudinary eager processing failed";
        }
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
    }
}
