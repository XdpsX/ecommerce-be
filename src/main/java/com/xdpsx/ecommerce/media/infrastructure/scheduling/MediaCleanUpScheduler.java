package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes Media assets that are no longer referenced.
 *
 * <p>The database record is deleted only after the storage provider confirms removal, so a provider
 * failure leaves a retryable {@code PENDING_DELETE} row behind instead of losing the provider identity.
 * No transaction wraps the provider calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaCleanUpScheduler {
    private final MediaStorage mediaStorage;
    private final MediaRepository mediaRepository;

    /**
     * This method is used to clean up media that are marked as deleted.
     * It runs every day at 00:05 AM.
     */
    @Scheduled(cron = "0 5 0 * * ?") // Every day at 00:05
    public void cleanUpDeletedMedia() {
        mediaRepository.findAllByStatus(MediaStatus.PENDING_DELETE).forEach(this::deleteAssetAndRecord);
    }

    /**
     * This method is used to clean up expired media that are marked as temporary
     * and have been created more than 1 day ago.
     * It runs every day at 01:05 AM.
     */
    @Scheduled(cron = "0 5 1 * * ?") // Every day at 01:05
    public void cleanUpExpiredMedia() {
        LocalDateTime expiryTime = LocalDateTime.now().minusDays(1);
        mediaRepository.findExpiredTemporaryMedia(expiryTime).forEach(media -> {
            // Persist the pending state first: if provider deletion fails, the next run can retry it.
            media.markPendingDeletion();
            mediaRepository.save(media);
            deleteAssetAndRecord(media);
        });
    }

    private void deleteAssetAndRecord(Media media) {
        try {
            mediaStorage.delete(media.getExternalId());
        } catch (MediaStorageException e) {
            log.error(
                    "Failed to delete media asset, mediaId={}, externalId={}; keeping the record for retry",
                    media.getId(),
                    media.getExternalId(),
                    e);
            return;
        }
        mediaRepository.delete(media);
    }
}
