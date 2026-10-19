package com.xdpsx.ecommerce.media.application.upload;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaUploadIntentService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    private final MediaUploadIntentPersistence persistence;
    private final MediaUploadAuthorizer authorizer;
    private final CurrentUserIdProvider currentUserIdProvider;
    private final MediaUploadProperties properties;
    private final Clock clock;

    /** Signs in memory, persists in one short REQUIRED transaction, and only returns after that commit succeeds. */
    public UploadIntentView create(CreateUploadIntentCommand command) {
        validate(command);
        String mediaId = UUID.randomUUID().toString();
        String publicId = command.purpose().resource() + "/" + mediaId;
        Long uploaderId = currentUserIdProvider.getRequiredUserId();
        MediaUploadAuthorizer.UploadAuthorization authorization = authorizer.authorize(
                new MediaUploadAuthorizer.UploadAuthorizationRequest(mediaId, publicId, command.purpose()));
        LocalDateTime intentExpiresAt =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plus(properties.getIntentLifetime());

        // This proxied bean commits before control returns; no usable signature escapes on persistence failure.
        persistence.persist(
                mediaId,
                publicId,
                uploaderId,
                command.purpose(),
                command.expectedContentType().trim().toLowerCase(Locale.ROOT),
                command.expectedSizeBytes(),
                intentExpiresAt,
                authorization.issuedAt(),
                authorization.expiresAt());

        return new UploadIntentView(
                mediaId,
                publicId,
                intentExpiresAt,
                MediaUploadStatus.PENDING_UPLOAD,
                MediaProcessingStatus.PENDING,
                MediaAttachmentStatus.TEMPORARY,
                authorization.expiresAt(),
                authorization.uploadUrl(),
                authorization.fields());
    }

    private void validate(CreateUploadIntentCommand command) {
        if (command == null || command.purpose() == null) {
            throw validationError("purpose", "required");
        }
        if (command.filename() == null
                || command.filename().trim().isEmpty()
                || command.filename().trim().length() > 255) {
            throw validationError("filename", "must contain between 1 and 255 characters");
        }
        if (command.expectedContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(
                        command.expectedContentType().trim().toLowerCase(Locale.ROOT))) {
            throw validationError("contentType", "unsupported image content type");
        }
        if (command.expectedSizeBytes() <= 0
                || command.expectedSizeBytes() > properties.getMaxFileSize().toBytes()) {
            throw validationError("sizeBytes", "outside the allowed range");
        }
    }

    private static ApplicationException validationError(String field, String reason) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, Map.of("field", field, "reason", reason));
    }
}
