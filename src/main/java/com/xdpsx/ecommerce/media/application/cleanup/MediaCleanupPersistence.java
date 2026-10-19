package com.xdpsx.ecommerce.media.application.cleanup;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import lombok.RequiredArgsConstructor;

/** Keeps cleanup selection, claims and final bookkeeping in short transactions outside provider calls. */
@Component
@RequiredArgsConstructor
public class MediaCleanupPersistence {
    private final MediaRepository repository;

    @Transactional(readOnly = true)
    public List<CleanupSnapshot> candidates(
            LocalDateTime now, LocalDateTime temporaryExpiry, LocalDateTime replayBoundary, int batchSize) {
        List<CleanupSnapshot> pendingDeletes =
                repository
                        .findPendingDeletionCleanupCandidates(now, replayBoundary, PageRequest.of(0, batchSize))
                        .stream()
                        .map(CleanupSnapshot::from)
                        .toList();
        List<CleanupSnapshot> expiredTemporary = repository
                .findExpiredTemporaryCleanupCandidates(temporaryExpiry, replayBoundary, PageRequest.of(0, batchSize))
                .stream()
                .map(CleanupSnapshot::from)
                .toList();

        // Reserve one due candidate per group before globally ordering the rest, so a backlog in one group cannot
        // consume the whole provider budget on every tick.
        List<CleanupSnapshot> candidates = new ArrayList<>();
        if (!pendingDeletes.isEmpty()) candidates.add(pendingDeletes.get(0));
        if (!expiredTemporary.isEmpty()) candidates.add(expiredTemporary.get(0));
        pendingDeletes.stream().skip(1).forEach(candidates::add);
        expiredTemporary.stream().skip(1).forEach(candidates::add);
        if (candidates.size() > 2) {
            List<CleanupSnapshot> fairFirst = new ArrayList<>(candidates.subList(0, 2));
            List<CleanupSnapshot> remainder = new ArrayList<>(candidates.subList(2, candidates.size()));
            remainder.sort(Comparator.comparing(CleanupSnapshot::effectiveDueAt).thenComparing(CleanupSnapshot::id));
            fairFirst.addAll(remainder);
            candidates = fairFirst;
        }
        return candidates.stream().limit(batchSize).toList();
    }

    @Transactional
    public CleanupSnapshot prepare(
            CleanupSnapshot expected,
            LocalDateTime now,
            LocalDateTime temporaryExpiry,
            java.time.Duration replayGrace) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (!matches(media, expected)) return null;
        if (media.getAttachmentStatus() == MediaAttachmentStatus.TEMPORARY) {
            if (media.getUploadStatus() != MediaUploadStatus.UPLOADED
                    || media.getAttachmentOwnerType() != null
                    || media.getAttachmentOwnerId() != null
                    || fallback(media).isAfter(temporaryExpiry)) return null;
            media.markPendingDeletion();
        }
        if (media.getAttachmentStatus() != MediaAttachmentStatus.PENDING_DELETE
                || !media.isPastReplayBoundary(now, replayGrace)) return null;
        if (media.getCleanupNextAttemptAt() != null
                && media.getCleanupNextAttemptAt().isAfter(now)) return null;
        return CleanupSnapshot.from(repository.save(media));
    }

    @Transactional
    public boolean finish(CleanupSnapshot expected, boolean signedTombstone, LocalDateTime nextCheckAt) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (!matches(media, expected) || media.getAttachmentStatus() != MediaAttachmentStatus.PENDING_DELETE) {
            return false;
        }
        if (signedTombstone) {
            media.scheduleCleanup(nextCheckAt);
            repository.save(media);
        } else {
            repository.delete(media);
        }
        return true;
    }

    @Transactional
    public void retry(CleanupSnapshot expected, LocalDateTime nextAttemptAt) {
        Media media = repository.findByIdForUpdate(expected.id()).orElse(null);
        if (!matches(media, expected) || media.getAttachmentStatus() != MediaAttachmentStatus.PENDING_DELETE) return;
        media.scheduleCleanup(nextAttemptAt);
        repository.save(media);
    }

    private static boolean matches(Media media, CleanupSnapshot expected) {
        return media != null
                && media.getExternalId().equals(expected.publicId())
                && java.util.Objects.equals(media.getAssetId(), expected.assetId())
                && media.isSignedUpload() == expected.signed()
                && media.getAttachmentStatus() == expected.attachmentStatus()
                && java.util.Objects.equals(media.getCleanupNextAttemptAt(), expected.cleanupNextAttemptAt());
    }

    private static LocalDateTime fallback(Media media) {
        return media.getUploadedAt() == null ? media.getCreatedAt() : media.getUploadedAt();
    }

    public record CleanupSnapshot(
            String id,
            String publicId,
            String assetId,
            boolean signed,
            MediaAttachmentStatus attachmentStatus,
            LocalDateTime cleanupNextAttemptAt,
            LocalDateTime effectiveDueAt) {
        public CleanupSnapshot(String id, String publicId, String assetId, boolean signed) {
            this(id, publicId, assetId, signed, MediaAttachmentStatus.PENDING_DELETE, null, LocalDateTime.MIN);
        }

        static CleanupSnapshot from(Media media) {
            LocalDateTime lifecycleAnchor = fallback(media);
            LocalDateTime effectiveDue =
                    media.getCleanupNextAttemptAt() == null ? lifecycleAnchor : media.getCleanupNextAttemptAt();
            return new CleanupSnapshot(
                    media.getId(),
                    media.getExternalId(),
                    media.getAssetId(),
                    media.isSignedUpload(),
                    media.getAttachmentStatus(),
                    media.getCleanupNextAttemptAt(),
                    effectiveDue);
        }
    }
}
