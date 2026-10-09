package com.xdpsx.ecommerce.media.domain;

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
    private String externalId;

    @Column(nullable = false)
    private String url;

    @Column(length = 100)
    private String caption;

    @Column(length = 30, nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private MediaPurpose purpose;

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

    /**
     * Attaches this Media to an aggregate. Only a temporary upload may be activated.
     *
     * @throws IllegalStateException when the Media is not {@link MediaAttachmentStatus#TEMPORARY}
     */
    public void activate() {
        if (attachmentStatus != MediaAttachmentStatus.TEMPORARY) {
            throw new IllegalStateException(
                    "Only temporary media can be activated, current status: " + attachmentStatus);
        }
        attachmentStatus = MediaAttachmentStatus.ACTIVE;
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
