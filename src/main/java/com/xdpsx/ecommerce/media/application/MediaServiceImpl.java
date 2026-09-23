package com.xdpsx.ecommerce.media.application;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.common.error.BadRequestException;
import com.xdpsx.ecommerce.common.error.EMessage;
import com.xdpsx.ecommerce.common.error.NotFoundException;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploader;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryUploadResponse;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {
    private final MediaRepository mediaRepository;
    private final CloudinaryUploader cloudinaryUploader;

    @Override
    public ViewMediaDTO createMedia(CreateMediaDTO request, MediaResourceType resourceType) {
        validateImageSize(request.file(), resourceType);
        CloudinaryUploadResponse response = null;
        try {
            response = cloudinaryUploader.uploadFile(request.file(), resourceType.getUploadOptions());
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
        } catch (Exception e) {
            if (response != null) {
                cloudinaryUploader.deleteFile(response.publicId());
            }
            throw new RuntimeException(EMessage.UPLOAD_IMAGE_FAILED.message());
        }
    }

    @Override
    public void deleteMedia(String id) {
        Media media = mediaRepository
                .findPublicMediaById(id)
                .orElseThrow(() -> new NotFoundException(EMessage.NOT_FOUND, id));
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
                throw new BadRequestException(EMessage.INVALID_IMAGE_WIDTH, resourceType.minWidth());
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read image file", e);
        }
    }
}
