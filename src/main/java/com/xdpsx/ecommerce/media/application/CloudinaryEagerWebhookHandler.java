package com.xdpsx.ecommerce.media.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerNotification;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryEagerWebhookHandler {
    private final ObjectMapper objectMapper;
    private final MediaRepository mediaRepository;

    @Transactional
    public Result handle(String rawBody) {
        CloudinaryEagerNotification notification = parse(rawBody);
        Media media = mediaRepository
                .findByProcessingReferenceForUpdate(notification.batchId())
                .orElse(null);
        if (media == null) {
            log.warn(
                    "Cloudinary eager notification arrived before media persistence, batchId={}",
                    notification.batchId());
            return Result.NOT_VISIBLE_YET;
        }

        CloudinaryEagerNotification.Outcome outcome = notification.outcome();
        if (media.getProcessingStatus() == MediaProcessingStatus.PROCESSING) {
            if (outcome == CloudinaryEagerNotification.Outcome.READY) {
                media.markProcessingReady();
            } else {
                media.markProcessingFailed(notification.failureReason());
            }
            return Result.APPLIED;
        }

        if ((media.getProcessingStatus() == MediaProcessingStatus.READY
                        && outcome == CloudinaryEagerNotification.Outcome.FAILED)
                || (media.getProcessingStatus() == MediaProcessingStatus.FAILED
                        && outcome == CloudinaryEagerNotification.Outcome.READY)) {
            log.warn(
                    "Ignoring conflicting Cloudinary eager notification, mediaId={}, batchId={}, currentStatus={}, incomingOutcome={}",
                    media.getId(),
                    notification.batchId(),
                    media.getProcessingStatus(),
                    outcome);
        }
        return Result.DUPLICATE_OR_CONFLICT;
    }

    private CloudinaryEagerNotification parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new IllegalArgumentException("Cloudinary notification body is empty");
        }
        try {
            return objectMapper.readValue(rawBody, CloudinaryEagerNotification.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cloudinary notification body is malformed", exception);
        }
    }

    public enum Result {
        APPLIED,
        DUPLICATE_OR_CONFLICT,
        NOT_VISIBLE_YET
    }
}
