package com.xdpsx.ecommerce.media.application.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class MediaCleanupPersistenceTest {
    @Mock
    private MediaRepository repository;

    @Test
    void candidates_ShouldReserveOneSlotForEachCleanupGroup() {
        Media pendingDelete = media("pending-delete", MediaAttachmentStatus.PENDING_DELETE);
        Media expiredTemporary = media("expired-temporary", MediaAttachmentStatus.TEMPORARY);
        when(repository.findPendingDeletionCleanupCandidates(any(), any(), any()))
                .thenReturn(List.of(pendingDelete));
        when(repository.findExpiredTemporaryCleanupCandidates(any(), any(), any()))
                .thenReturn(List.of(expiredTemporary));

        List<MediaCleanupPersistence.CleanupSnapshot> candidates = new MediaCleanupPersistence(repository)
                .candidates(LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), 2);

        assertThat(candidates)
                .extracting(MediaCleanupPersistence.CleanupSnapshot::attachmentStatus)
                .containsExactly(MediaAttachmentStatus.PENDING_DELETE, MediaAttachmentStatus.TEMPORARY);
    }

    @Test
    void prepare_ShouldIgnoreCandidateWhenAnotherWorkerRescheduledIt() {
        LocalDateTime now = LocalDateTime.now();
        Media media = media("rescheduled", MediaAttachmentStatus.PENDING_DELETE);
        media.scheduleCleanup(now.plusHours(1));
        var stale = new MediaCleanupPersistence.CleanupSnapshot(
                "rescheduled", "public/rescheduled", null, false, MediaAttachmentStatus.PENDING_DELETE, null, now);
        when(repository.findByIdForUpdate("rescheduled")).thenReturn(Optional.of(media));

        assertThat(new MediaCleanupPersistence(repository)
                        .prepare(stale, now, now.minusDays(1), java.time.Duration.ofMinutes(10)))
                .isNull();
        verify(repository, never()).save(any(Media.class));
    }

    private static Media media(String id, MediaAttachmentStatus status) {
        return Media.builder()
                .id(id)
                .externalId("public/" + id)
                .url("https://cdn.example.test/" + id + ".png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .attachmentStatus(status)
                .build();
    }
}
