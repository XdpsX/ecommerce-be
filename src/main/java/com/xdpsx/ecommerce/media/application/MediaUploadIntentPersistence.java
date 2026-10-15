package com.xdpsx.ecommerce.media.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

/** Isolates the intent write so its REQUIRED transaction commits before authorization is returned. */
@Component
@RequiredArgsConstructor
public class MediaUploadIntentPersistence {
    private final MediaRepository mediaRepository;

    @Transactional
    public void persist(
            String mediaId,
            String publicId,
            Long uploaderId,
            MediaPurpose purpose,
            String contentType,
            long sizeBytes,
            LocalDateTime intentExpiresAt,
            LocalDateTime authorizationIssuedAt,
            LocalDateTime authorizationExpiresAt) {
        mediaRepository.save(Media.createSignedPendingUpload(
                mediaId,
                publicId,
                uploaderId,
                purpose,
                contentType,
                sizeBytes,
                intentExpiresAt,
                authorizationIssuedAt,
                authorizationExpiresAt));
    }
}
