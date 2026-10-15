package com.xdpsx.ecommerce.media.infrastructure.scheduling;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
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
    private final MediaUploadProperties uploadProperties;
    private final Clock clock;

    /**
     * This method is used to clean up media that are marked as deleted.
     * It runs every day at 00:05 AM.
     */
    @Scheduled(cron = "0 5 0 * * ?") // Every day at 00:05
    public void cleanUpDeletedMedia() {
        mediaRepository
                .findAllByAttachmentStatusBeforeReplayBoundary(
                        MediaAttachmentStatus.PENDING_DELETE, now().minus(uploadProperties.getReplayGrace()))
                .forEach(this::deleteAssetAndRecord);
    }

    /**
     * This method is used to clean up expired media that are marked as temporary
     * and have been created more than 1 day ago.
     * It runs every day at 01:05 AM.
     */
    @Scheduled(cron = "0 5 1 * * ?") // Every day at 01:05
    public void cleanUpExpiredMedia() {
        LocalDateTime expiryTime = now().minusDays(1);
        LocalDateTime replayBoundary = now().minus(uploadProperties.getReplayGrace());
        mediaRepository.findExpiredTemporaryMedia(expiryTime, replayBoundary).forEach(media -> {
            // Claim in a separate short transaction. An attachment that wins the race changes the row to ACTIVE,
            // making this update affect zero rows and preventing deletion of an in-use asset.
            if (mediaRepository.claimExpiredTemporaryForDeletion(media.getId(), expiryTime) == 1) {
                deleteAssetAndRecord(media);
            }
        });
    }

    private void deleteAssetAndRecord(Media media) {
        // Defensive invariant in case a query is later weakened or a stale candidate is passed in.
        if (!media.isPastReplayBoundary(now(), uploadProperties.getReplayGrace())) return;
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
