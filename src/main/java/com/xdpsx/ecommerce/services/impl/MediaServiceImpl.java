package com.xdpsx.ecommerce.services.impl;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.constants.messages.EMessage;
import com.xdpsx.ecommerce.dtos.media.CloudinaryUploadResponse;
import com.xdpsx.ecommerce.dtos.media.CreateMediaDTO;
import com.xdpsx.ecommerce.dtos.media.ViewMediaDTO;
import com.xdpsx.ecommerce.entities.Media;
import com.xdpsx.ecommerce.entities.enums.MediaResourceType;
import com.xdpsx.ecommerce.exceptions.BadRequestException;
import com.xdpsx.ecommerce.exceptions.NotFoundException;
import com.xdpsx.ecommerce.mappers.MediaMapper;
import com.xdpsx.ecommerce.repositories.MediaRepository;
import com.xdpsx.ecommerce.services.MediaService;
import com.xdpsx.ecommerce.utils.CloudinaryUploader;

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
