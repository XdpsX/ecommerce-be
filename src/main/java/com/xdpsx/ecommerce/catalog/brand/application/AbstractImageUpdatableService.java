package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.Map;

import com.xdpsx.ecommerce.catalog.brand.domain.HasImage;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

public abstract class AbstractImageUpdatableService {

    protected MediaRepository mediaRepository;

    public AbstractImageUpdatableService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    protected <T extends HasImage> void updateImage(T entity, String newImageId, MediaPurpose expectedPurpose) {
        Media oldImage = entity.getImage();

        if (oldImage == null && newImageId == null) return;

        if (oldImage != null && !oldImage.getId().equals(newImageId)) {
            oldImage.markPendingDeletion();
            mediaRepository.save(oldImage);
            entity.setImage(null);
        }

        if (newImageId != null && (oldImage == null || !oldImage.getId().equals(newImageId))) {
            Media newImage = mediaRepository
                    .findAttachableById(newImageId, expectedPurpose)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", newImageId)));

            newImage.activate();
            mediaRepository.save(newImage);

            entity.setImage(newImage);
        }
    }
}
