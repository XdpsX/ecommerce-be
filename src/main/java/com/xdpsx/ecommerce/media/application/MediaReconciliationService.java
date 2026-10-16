package com.xdpsx.ecommerce.media.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.config.MediaReconciliationProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.ProcessingInspection;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadPersistence.Snapshot;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryMediaTransformations;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.extern.slf4j.Slf4j;

/** Runs bounded provider inspection outside transactions; persistence methods lock and recheck each merge. */
@Service
@Slf4j
public class MediaReconciliationService {
    private final MediaRepository repository;
    private final MediaDirectUploadPersistence persistence;
    private final MediaAssetInspector inspector;
    private final MediaUploadedAssetValidator assetValidator;
    private final MediaUploadProperties uploadProperties;
    private final MediaReconciliationProperties properties;
    private final Clock clock;
    private final AtomicBoolean processingFirst = new AtomicBoolean(false);

    public MediaReconciliationService(
            MediaRepository repository,
            MediaDirectUploadPersistence persistence,
            MediaAssetInspector inspector,
            MediaUploadedAssetValidator assetValidator,
            MediaUploadProperties uploadProperties,
            MediaReconciliationProperties properties,
            Clock clock) {
        this.repository = repository;
        this.persistence = persistence;
        this.inspector = inspector;
        this.assetValidator = assetValidator;
        this.uploadProperties = uploadProperties;
        this.properties = properties;
        this.clock = clock;
    }

    public TickResult runOnce() {
        LocalDateTime now = now();
        List<Snapshot> pending = repository
                .findPendingUploadReconciliationCandidates(
                        now.minus(uploadProperties.getCompletionGrace()),
                        now,
                        uploadProperties.getCompletionGrace().toSeconds(),
                        properties.getBatchSize())
                .stream()
                .map(Snapshot::from)
                .toList();
        List<Snapshot> processing = repository
                .findProcessingReconciliationCandidates(
                        now.minus(properties.getProcessingTimeout()),
                        now.minus(properties.getProcessingRecoveryHorizon()),
                        now,
                        properties.getProcessingTimeout().toSeconds(),
                        properties.getBatchSize())
                .stream()
                .map(Snapshot::from)
                .toList();
        repository
                .findProcessingPastRecoveryHorizon(now.minus(properties.getProcessingRecoveryHorizon()), 10)
                .forEach(media -> log.warn(
                        "Media processing reconciliation is past automatic recovery horizon, mediaId={}",
                        media.getId()));

        int budget = properties.getMaxProviderRequestsPerRun();
        int calls = 0;
        int pendingIndex = 0;
        int processingIndex = 0;
        boolean stopTick = false;
        boolean processFirst = processingFirst.getAndSet(!processingFirst.get());
        while (!stopTick && calls < budget && (pendingIndex < pending.size() || processingIndex < processing.size())) {
            boolean preferProcessing = processFirst;
            if (preferProcessing && processingIndex < processing.size()) {
                ProviderCallResult result = reconcileProcessing(processing.get(processingIndex++), budget - calls);
                calls += result.requests();
                stopTick = result.rateLimited();
                processFirst = false;
            } else if (!preferProcessing && pendingIndex < pending.size()) {
                ProviderCallResult result = reconcilePending(pending.get(pendingIndex++));
                calls += result.requests();
                stopTick = result.rateLimited();
                processFirst = true;
            } else if (processingIndex < processing.size()) {
                ProviderCallResult result = reconcileProcessing(processing.get(processingIndex++), budget - calls);
                calls += result.requests();
                stopTick = result.rateLimited();
                processFirst = false;
            } else if (pendingIndex < pending.size()) {
                ProviderCallResult result = reconcilePending(pending.get(pendingIndex++));
                calls += result.requests();
                stopTick = result.rateLimited();
                processFirst = true;
            }
        }
        return new TickResult(calls, pendingIndex, processingIndex);
    }

    private ProviderCallResult reconcilePending(Snapshot snapshot) {
        try {
            InspectedMediaAsset asset = inspector.inspect(snapshot.publicId());
            if (asset == null) {
                LocalDateTime current = now();
                LocalDateTime next = current.plus(pendingUploadBackoff(snapshot, current));
                persistence.claimAbsentRecovery(snapshot, current, next);
                return new ProviderCallResult(1, false);
            }
            String contentType = assetValidator.validate(snapshot, asset);
            if (contentType == null) {
                persistence.rejectRecovery(snapshot, "Cloudinary asset does not satisfy the signed upload policy");
            } else {
                persistence.recoverFinalize(snapshot, asset, contentType);
            }
            return new ProviderCallResult(1, false);
        } catch (InvalidMediaAssetResponseException exception) {
            persistence.rejectRecovery(snapshot, "Cloudinary asset response omitted required metadata");
            return new ProviderCallResult(1, false);
        } catch (MediaAssetProviderException exception) {
            LocalDateTime current = now();
            persistence.scheduleUploadRetry(snapshot, current.plus(pendingUploadBackoff(snapshot, current)));
            boolean rateLimited = isRateLimit(exception);
            log.warn("Cloudinary upload recovery deferred, mediaId={}", snapshot.id());
            return new ProviderCallResult(exception.requestsAttempted(), rateLimited);
        }
    }

    private ProviderCallResult reconcileProcessing(Snapshot snapshot, int remainingRequestBudget) {
        if (remainingRequestBudget <= 0) return new ProviderCallResult(0, false);
        int pageLimit = Math.min(properties.getMaxDerivedPagesPerAsset(), remainingRequestBudget);
        try {
            List<String> expected = CloudinaryMediaTransformations.eagerStringsFor(snapshot.purpose());
            ProcessingInspection result =
                    inspector.inspectProcessing(snapshot.publicId(), pageLimit, java.util.Set.copyOf(expected));
            int calls = result == null ? 1 : Math.max(1, result.pagesRead());
            boolean identityMatches = result != null
                    && result.resourceFound()
                    && snapshot.publicId().equals(result.publicId())
                    && "image".equals(result.resourceType())
                    && "upload".equals(result.deliveryType())
                    && result.assetId() != null
                    && !result.assetId().isBlank()
                    && (snapshot.assetId() == null || snapshot.assetId().equals(result.assetId()));
            if (identityMatches && result.generatedTransformations().containsAll(expected)) {
                persistence.reconcileProcessingReady(snapshot, result.assetId());
            } else {
                scheduleProcessingRetry(snapshot, now(), properties.getProviderErrorBackoff());
                if (result == null || !identityMatches || !result.inventoryComplete()) {
                    log.warn("Cloudinary processing inspection inconclusive, mediaId={}", snapshot.id());
                }
            }
            return new ProviderCallResult(calls, false);
        } catch (MediaAssetProviderException exception) {
            scheduleProcessingRetry(snapshot, now(), properties.getProviderErrorBackoff());
            boolean rateLimited = isRateLimit(exception);
            log.warn("Cloudinary processing reconciliation deferred, mediaId={}", snapshot.id());
            return new ProviderCallResult(exception.requestsAttempted(), rateLimited);
        } catch (InvalidMediaAssetResponseException exception) {
            scheduleProcessingRetry(snapshot, now(), properties.getProviderErrorBackoff());
            log.warn("Cloudinary processing response was inconclusive, mediaId={}", snapshot.id());
            return new ProviderCallResult(1, false);
        }
    }

    private void scheduleProcessingRetry(Snapshot snapshot, LocalDateTime current, Duration minimumDelay) {
        int nextAttempt = snapshot.processingAttempts() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : snapshot.processingAttempts() + 1;
        Duration delay = backoff(properties.getRetryDelay(), properties.getMaxRetryDelay(), nextAttempt);
        if (delay.compareTo(minimumDelay) < 0) delay = minimumDelay;
        if (delay.compareTo(properties.getMaxRetryDelay()) > 0) delay = properties.getMaxRetryDelay();
        persistence.scheduleProcessingRetry(snapshot, current.plus(delay));
    }

    private Duration pendingUploadBackoff(Snapshot snapshot, LocalDateTime current) {
        LocalDateTime due = snapshot.authorizationExpiresAt().plus(uploadProperties.getCompletionGrace());
        if (current.isAfter(due.plus(properties.getPendingUploadRetryWindow()))) {
            return properties.getPendingUploadRetryMaxDelay();
        }
        return properties.getPendingUploadRetryDelay();
    }

    private static Duration backoff(Duration base, Duration cap, int attempt) {
        Duration delay = base;
        for (int i = 1; i < attempt && delay.compareTo(cap) < 0; i++) {
            if (delay.compareTo(cap.dividedBy(2)) > 0) return cap;
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(cap) > 0 ? cap : delay;
    }

    private static boolean isRateLimit(MediaAssetProviderException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("429") || message.toLowerCase().contains("rate limit"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record ProviderCallResult(int requests, boolean rateLimited) {}

    public record TickResult(int providerRequests, int pendingRowsVisited, int processingRowsVisited) {}
}
