package com.xdpsx.ecommerce.media.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerNotification;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerOutcomeEvaluator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public class CloudinaryEagerWebhookHandler {
    private final ObjectMapper objectMapper;
    private final CloudinaryWebhookPersistence persistence;
    private final CloudinaryEagerOutcomeEvaluator outcomeEvaluator;
    private final CloudinaryBatchReferenceBinder batchReferenceBinder;

    public CloudinaryEagerWebhookHandler(
            ObjectMapper objectMapper,
            CloudinaryWebhookPersistence persistence,
            CloudinaryEagerOutcomeEvaluator outcomeEvaluator,
            CloudinaryBatchReferenceBinder batchReferenceBinder) {
        this.objectMapper = objectMapper;
        this.persistence = persistence;
        this.outcomeEvaluator = outcomeEvaluator;
        this.batchReferenceBinder = batchReferenceBinder;
    }

    public Result handle(String rawBody) {
        CloudinaryEagerNotification notification = parse(rawBody);
        if (!notification.isValidEagerType()
                || !notification.hasIdentity()
                || (notification.publicId() != null && notification.publicId().isBlank())) {
            throw new IllegalArgumentException("Invalid Cloudinary eager notification identity");
        }
        final CloudinaryWebhookPersistence.MergeResult merged;
        try {
            merged = persistence.merge(notification, outcomeEvaluator);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Ignoring Cloudinary callback that conflicts with an existing asset identity");
            return Result.DUPLICATE_OR_CONFLICT;
        }
        if (merged.batchReferenceToBind() != null) {
            try {
                batchReferenceBinder.bindIfAbsent(merged.mediaId(), merged.batchReferenceToBind());
            } catch (RuntimeException exception) {
                // The primary identity and processing merge is already committed. Batch ID is trace-only.
                log.warn("Unable to bind optional Cloudinary batch reference, mediaId={}", merged.mediaId());
            }
        }
        return merged.result();
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
        IGNORED,
        NOT_VISIBLE_YET
    }
}
