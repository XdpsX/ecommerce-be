package com.xdpsx.ecommerce.media.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaUploadIntentService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    private final MediaRepository mediaRepository;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final MediaUploadProperties properties;
    private final Clock pricingClock;

    /**
     * Persists an intent in its own short transaction. The proxy commits this method before returning, including
     * when a future caller already has a transaction; PR C must only sign after this invocation returns.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadIntentView create(CreateUploadIntentCommand command) {
        validate(command);

        String mediaId = UUID.randomUUID().toString();
        String publicId = command.purpose().resource() + "/" + mediaId;
        LocalDateTime intentExpiresAt = LocalDateTime.now(pricingClock).plus(properties.getIntentLifetime());
        Media media = Media.createPendingUpload(
                mediaId,
                publicId,
                currentUserIdProvider.getRequiredUserId(),
                command.purpose(),
                command.expectedContentType().trim(),
                command.expectedSizeBytes(),
                intentExpiresAt);
        Media saved = mediaRepository.save(media);
        return new UploadIntentView(
                saved.getId(),
                saved.getExternalId(),
                saved.getIntentExpiresAt(),
                saved.getUploadStatus(),
                saved.getProcessingStatus(),
                saved.getAttachmentStatus());
    }

    private void validate(CreateUploadIntentCommand command) {
        if (command == null || command.purpose() == null) {
            throw new IllegalArgumentException("A media purpose is required");
        }
        if (command.filename() == null
                || command.filename().trim().isEmpty()
                || command.filename().trim().length() > 255) {
            throw new IllegalArgumentException("Filename must contain between 1 and 255 characters");
        }
        if (command.expectedContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(
                        command.expectedContentType().trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported image content type");
        }
        if (command.expectedSizeBytes() <= 0
                || command.expectedSizeBytes() > properties.getMaxFileSize().toBytes()) {
            throw new IllegalArgumentException("Expected file size is outside the allowed range");
        }
    }
}
