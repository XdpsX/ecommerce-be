package com.xdpsx.ecommerce.media.application;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.UploadedMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.application.storage.MediaUploadCommand;
import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.application.storage.StoredMedia;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {
    private final MediaRepository mediaRepository;
    private final MediaStorage mediaStorage;
    private final MediaUrlGenerator mediaUrlGenerator;
    private final CurrentUserIdProvider currentUserIdProvider;

    @Override
    public UploadedMediaDTO createMedia(CreateMediaDTO request, MediaPurpose purpose) {
        validateImageSize(request.file(), purpose);
        Long uploadedByUserId = currentUserIdProvider.getRequiredUserId();

        // Only a confirmed media-provider failure becomes MEDIA_UPLOAD_FAILED.
        StoredMedia storedMedia;
        try {
            storedMedia = mediaStorage.upload(new MediaUploadCommand(request.file(), purpose));
        } catch (MediaStorageException e) {
            // Keep the cause internally; never expose the provider message to the client.
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_FAILED, e);
        }

        Media media = Media.createUploaded(
                UUID.randomUUID().toString(),
                storedMedia.externalId(),
                storedMedia.url(),
                request.caption(),
                request.file().getContentType(),
                purpose,
                uploadedByUserId,
                MediaAttachmentStatus.TEMPORARY,
                storedMedia.processingReference());

        Media savedMedia;
        try {
            savedMedia = mediaRepository.save(media);
        } catch (RuntimeException e) {
            // Never persisted, so the uploaded asset is unreachable: remove it, preserving the original cause
            // (surfaced as INTERNAL_ERROR at the API boundary).
            cleanupQuietly(storedMedia.externalId(), e);
            throw e;
        }

        // Mapping happens after a successful insert and must not trigger compensation: the persisted row
        // already references the asset, and a temporary upload is cleaned up by TTL if it is never returned.
        ViewMediaDTO mapped = MediaMapper.INSTANCE.toViewMediaDTO(savedMedia);
        return new UploadedMediaDTO(
                mapped.id(),
                mapped.caption(),
                mapped.contentType(),
                mapped.url(),
                mediaUrlGenerator.generateVariants(savedMedia.getExternalId(), savedMedia.getPurpose()));
    }

    private void cleanupQuietly(String externalId, RuntimeException cause) {
        try {
            mediaStorage.delete(externalId);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void deleteMedia(String id) {
        // Only temporary uploads may be discarded through the Media API; active Media belongs to an aggregate.
        if (mediaRepository.claimTemporaryForDeletion(id, currentUserIdProvider.getRequiredUserId()) == 0) {
            throw new ApplicationException(
                    ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", id));
        }
    }

    private void validateImageSize(MultipartFile file, MediaPurpose purpose) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IllegalArgumentException("Invalid image format");
            }

            int width = image.getWidth();
            if (purpose.minWidth() != null && width < purpose.minWidth()) {
                throw new ApplicationException(ErrorCode.INVALID_IMAGE_WIDTH, Map.of("minWidth", purpose.minWidth()));
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read image file", e);
        }
    }
}
