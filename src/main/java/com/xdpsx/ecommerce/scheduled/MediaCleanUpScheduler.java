package com.xdpsx.ecommerce.scheduled;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.repositories.MediaRepository;
import com.xdpsx.ecommerce.utils.CloudinaryUploader;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MediaCleanUpScheduler {
    private final CloudinaryUploader cloudinaryUploader;
    private final MediaRepository mediaRepository;

    /**
     * This method is used to clean up media that are marked as deleted.
     * It runs every day at 00:05 AM.
     */
    @Scheduled(cron = "0 5 0 * * ?") // Every day at 00:05
    public void cleanUpDeletedMedia() {
        mediaRepository.findDeletedMedia().forEach(media -> {
            cloudinaryUploader.deleteFile(media.getExternalId());
            mediaRepository.delete(media);
        });
    }

    /**
     * This method is used to clean up expired media that are marked as temporary
     * and have been created more than 1 day ago.
     * It runs every day at 01:05 AM.
     */
    @Scheduled(cron = "0 5 1 * * ?") // Every day at 01:05
    public void cleanUpExpiredMedia() {
        LocalDateTime expiryTime = LocalDateTime.now().minusDays(1);
        mediaRepository.findExpiredMedia(expiryTime).forEach(media -> {
            cloudinaryUploader.deleteFile(media.getExternalId());
            mediaRepository.delete(media);
        });
    }
}
