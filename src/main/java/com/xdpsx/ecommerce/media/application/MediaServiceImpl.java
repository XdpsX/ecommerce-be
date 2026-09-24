package com.xdpsx.ecommerce.media.application;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploadResponse;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploader;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {
    private final MediaRepository mediaRepository;
    private final CloudinaryUploader cloudinaryUploader;

    @Override
    public ViewMediaDTO createMedia(CreateMediaDTO request, MediaResourceType resourceType) {
        validateImageSize(request.file(), resourceType);

        // Only a confirmed media-provider upload failure becomes MEDIA_UPLOAD_FAILED.
        CloudinaryUploadResponse response;
        try {
            response = cloudinaryUploader.uploadFile(request.file(), resourceType.getUploadOptions());
        } catch (RuntimeException e) {
            // Keep the cause internally; never expose the provider message to the client.
            throw new ApplicationException(ErrorCode.MEDIA_UPLOAD_FAILED, e);
        }

        try {
            Media media = Media.builder()
                    .id(response.displayName())
                    .externalId(response.publicId())
                    .url(response.url())
                    .caption(request.caption())
                    .contentType(request.file().getContentType())
                    .resourceType(resourceType)
                    .tempFlg(true)
                    .deleteFlg(false)
                    .build();
            Media savedMedia = mediaRepository.save(media);
            return MediaMapper.INSTANCE.toViewMediaDTO(savedMedia);
        } catch (RuntimeException e) {
            // Persistence or mapping failure: clean up the uploaded file preserving the original cause,
            // then let the original failure propagate (handled as INTERNAL_ERROR at the API boundary).
            cleanupQuietly(response.publicId(), e);
            throw e;
        }
    }

    private void cleanupQuietly(String publicId, RuntimeException cause) {
        try {
            cloudinaryUploader.deleteFile(publicId);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void deleteMedia(String id) {
        Media media = mediaRepository
                .findPublicMediaById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", id)));
        media.setDeleteFlg(true);
        mediaRepository.save(media);
    }

    private void validateImageSize(MultipartFile file, MediaResourceType resourceType) {
        if (resourceType.minWidth() == null) {
            return;
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IllegalArgumentException("Invalid image format");
            }

            int width = image.getWidth();
            if (width < resourceType.minWidth()) {
                throw new ApplicationException(
                        ErrorCode.INVALID_IMAGE_WIDTH, Map.of("minWidth", resourceType.minWidth()));
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read image file", e);
        }
    }
}
