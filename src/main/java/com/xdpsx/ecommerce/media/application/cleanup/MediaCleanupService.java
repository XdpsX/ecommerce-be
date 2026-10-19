package com.xdpsx.ecommerce.media.application.cleanup;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.config.MediaCleanupProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Bounded, replay-safe cleanup orchestration; no database transaction spans provider calls. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupService {
    private static final int SIGNED_RESOURCE_TYPES_PER_INSPECTION = 3;
    private static final int LEGACY_RESOURCE_TYPES_PER_INSPECTION = 1;
    private static final int DELETE_REQUESTS_PER_ATTEMPT = 1;

    private final MediaCleanupPersistence persistence;
    private final MediaCleanupProvider provider;
    private final MediaCleanupProperties properties;
    private final MediaUploadProperties uploadProperties;
    private final Clock clock;

    public void runCleanup() {
        LocalDateTime now = now();
        LocalDateTime expiry = now.minus(properties.getTemporaryLifetime());
        LocalDateTime replayBoundary = now.minus(uploadProperties.getReplayGrace());
        List<MediaCleanupPersistence.CleanupSnapshot> candidates =
                persistence.candidates(now, expiry, replayBoundary, properties.getBatchSize());
        int requestsRemaining = properties.getProviderRequestBudget();
        for (MediaCleanupPersistence.CleanupSnapshot candidate : candidates) {
            int inspectionRequests =
                    candidate.signed() ? SIGNED_RESOURCE_TYPES_PER_INSPECTION : LEGACY_RESOURCE_TYPES_PER_INSPECTION;
            // Reserve exact inspection requests for this identity scope and one batched exact delete request.
            if (requestsRemaining < inspectionRequests + DELETE_REQUESTS_PER_ATTEMPT) break;
            MediaCleanupPersistence.CleanupSnapshot target =
                    persistence.prepare(candidate, now(), expiry, uploadProperties.getReplayGrace());
            if (target == null) continue;
            int attemptRequests = inspectionRequests;
            try {
                MediaCleanupProvider.CleanupScope scope = target.signed()
                        ? MediaCleanupProvider.CleanupScope.BACKEND_ISSUED
                        : MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY;
                MediaCleanupProvider.Inspection inspection = provider.inspectExactPublicId(target.publicId(), scope);
                if (!inspection.isAbsent()) {
                    attemptRequests += DELETE_REQUESTS_PER_ATTEMPT;
                    provider.deleteExactAssets(inspection.assets());
                }
                requestsRemaining -= attemptRequests;
                LocalDateTime next = now().plus(
                                target.signed() ? properties.getSignedTombstoneRecheck() : properties.getRetryDelay());
                persistence.finish(target, target.signed(), next);
            } catch (MediaCleanupProviderException exception) {
                requestsRemaining -= attemptRequests;
                persistence.retry(target, now().plus(properties.getRetryDelay()));
                log.warn("Media cleanup deferred, mediaId={}, rateLimited={}", target.id(), exception.isRateLimited());
                if (exception.isRateLimited()) break;
            } catch (RuntimeException exception) {
                requestsRemaining -= attemptRequests;
                persistence.retry(target, now().plus(properties.getRetryDelay()));
                log.error(
                        "Unexpected media cleanup failure, mediaId={}; keeping tombstone for retry",
                        target.id(),
                        exception);
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
