package com.xdpsx.ecommerce.media.application.processing;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.persistence.MediaRepository;

/** Binds trace-only callback correlation after the primary webhook transaction has committed. */
@Component
public class CloudinaryBatchReferenceBinder {
    private final MediaRepository mediaRepository;

    public CloudinaryBatchReferenceBinder(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindIfAbsent(String mediaId, String batchId) {
        mediaRepository.bindProcessingReferenceIfAbsent(mediaId, batchId);
    }
}
