package com.xdpsx.ecommerce.catalog.brand.application;

import java.util.Map;

import com.xdpsx.ecommerce.catalog.brand.domain.HasImage;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

public abstract class AbstractImageUpdatableService {

    protected MediaRepository mediaRepository;

    public AbstractImageUpdatableService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    protected <T extends HasImage> void updateImage(T entity, String newImageId, MediaResourceType expectedType) {
        Media oldImage = entity.getImage();

        if (oldImage == null && newImageId == null) return;

        if (oldImage != null && !oldImage.getId().equals(newImageId)) {
            oldImage.setDeleteFlg(true);
            mediaRepository.save(oldImage);
            entity.setImage(null);
        }

        if (newImageId != null && (oldImage == null || !oldImage.getId().equals(newImageId))) {
            Media newImage = mediaRepository
                    .findPublicTempMediaById(newImageId, expectedType)
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "media", "resourceId", newImageId)));

            newImage.setTempFlg(false);
            mediaRepository.save(newImage);

            entity.setImage(newImage);
        }
    }
}
