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
    @Column(length = 32, nullable = false)
    private MediaStatus status;

    /**
     * Attaches this Media to an aggregate. Only a temporary upload may be activated.
     *
     * @throws IllegalStateException when the Media is not {@link MediaStatus#TEMPORARY}
     */
    public void activate() {
        if (status != MediaStatus.TEMPORARY) {
            throw new IllegalStateException("Only temporary media can be activated, current status: " + status);
        }
        status = MediaStatus.ACTIVE;
    }

    /**
     * Requests removal of the stored asset. Idempotent for {@link MediaStatus#PENDING_DELETE}; the
     * physical asset is removed later by the cleanup process.
     *
     * @throws IllegalStateException when the status has not been loaded
     */
    public void markPendingDeletion() {
        if (status == null) {
            throw new IllegalStateException("Media status is not set");
        }
        status = MediaStatus.PENDING_DELETE;
    }
}
