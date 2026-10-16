package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.config.MediaReconciliationProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.ProcessingInspection;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadPersistence.Snapshot;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

class MediaReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T02:30:00Z");

    @Test
    void runOnce_ShouldBoundProviderCallsAndSaturateExponentialBackoff() {
        MediaRepository repository = mock(MediaRepository.class);
        MediaDirectUploadPersistence persistence = mock(MediaDirectUploadPersistence.class);
        MediaAssetInspector inspector = mock(MediaAssetInspector.class);
        MediaReconciliationProperties properties = new MediaReconciliationProperties();
        properties.setMaxProviderRequestsPerRun(2);
        Media media = Media.builder()
                .id("media-1")
                .externalId("product/media-1")
                .assetId("asset-1")
                .url("https://cdn.example.test/media.png")
                .contentType("image/png")
                .purpose(MediaPurpose.PRODUCT_IMAGE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .uploadedAt(LocalDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC))
                .processingReconciliationAttempts(Integer.MAX_VALUE)
                .build();
        when(repository.findPendingUploadReconciliationCandidates(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(repository.findProcessingReconciliationCandidates(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(media));
        when(repository.findProcessingPastRecoveryHorizon(any(), anyInt())).thenReturn(List.of());
        when(inspector.inspectProcessing(eq("product/media-1"), eq(2), any()))
                .thenReturn(new ProcessingInspection(
                        "product/media-1", "asset-1", "image", "upload", Set.of("partial"), false, 2));

        MediaReconciliationService service = new MediaReconciliationService(
                repository,
                persistence,
                inspector,
                new MediaUploadedAssetValidator(new MediaUploadProperties()),
                new MediaUploadProperties(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.runOnce();
        Snapshot snapshot = new Snapshot(
                "media-1",
                "product/media-1",
                null,
                MediaPurpose.PRODUCT_IMAGE,
                MediaUploadStatus.UPLOADED,
                MediaProcessingStatus.PROCESSING,
                MediaAttachmentStatus.TEMPORARY,
                "https://cdn.example.test/media.png",
                "asset-1",
                null,
                null,
                null,
                LocalDateTime.ofInstant(NOW.minusSeconds(3600), ZoneOffset.UTC),
                null,
                null,
                Integer.MAX_VALUE,
                null);

        assertEquals(2, result.providerRequests());
        verify(persistence)
                .scheduleProcessingRetry(
                        eq(snapshot), eq(LocalDateTime.ofInstant(NOW.plusSeconds(86400), ZoneOffset.UTC)));
    }

    @Test
    void runOnce_ShouldSplitSmallRequestBudgetAcrossCandidateGroupsAcrossTicks() {
        MediaRepository repository = mock(MediaRepository.class);
        MediaDirectUploadPersistence persistence = mock(MediaDirectUploadPersistence.class);
        MediaAssetInspector inspector = mock(MediaAssetInspector.class);
        MediaReconciliationProperties properties = new MediaReconciliationProperties();
        properties.setMaxProviderRequestsPerRun(1);
        LocalDateTime issued = LocalDateTime.ofInstant(NOW.minusSeconds(7200), ZoneOffset.UTC);
        Media pending = Media.createSignedPendingUpload(
                "pending-fair",
                "product/pending-fair",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                100,
                issued.plusHours(2),
                issued,
                issued.plusHours(1));
        Media processing = Media.builder()
                .id("processing-fair")
                .externalId("product/processing-fair")
                .assetId("asset-fair")
                .url("https://cdn.example.test/image.png")
                .contentType("image/png")
                .purpose(MediaPurpose.CATEGORY_IMAGE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .processingStatus(MediaProcessingStatus.PROCESSING)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .build();
        when(repository.findPendingUploadReconciliationCandidates(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(pending));
        when(repository.findProcessingReconciliationCandidates(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(processing));
        when(repository.findProcessingPastRecoveryHorizon(any(), anyInt())).thenReturn(List.of());
        when(inspector.inspect("product/pending-fair")).thenReturn(null);
        when(inspector.inspectProcessing(eq("product/processing-fair"), eq(1), any()))
                .thenReturn(new ProcessingInspection(
                        "product/processing-fair", "asset-fair", "image", "upload", Set.of(), false, 1));
        MediaReconciliationService service = new MediaReconciliationService(
                repository,
                persistence,
                inspector,
                new MediaUploadedAssetValidator(new MediaUploadProperties()),
                new MediaUploadProperties(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(1, service.runOnce().providerRequests());
        assertEquals(1, service.runOnce().providerRequests());
        verify(inspector).inspect("product/pending-fair");
        verify(inspector).inspectProcessing(eq("product/processing-fair"), eq(1), any());
    }

    @Test
    void runOnce_ShouldStopPendingUploadInspectionsAfterRateLimit() {
        MediaRepository repository = mock(MediaRepository.class);
        MediaDirectUploadPersistence persistence = mock(MediaDirectUploadPersistence.class);
        MediaAssetInspector inspector = mock(MediaAssetInspector.class);
        MediaReconciliationProperties properties = new MediaReconciliationProperties();
        properties.setMaxProviderRequestsPerRun(20);
        Media first = pendingUpload("pending-429-first");
        Media second = pendingUpload("pending-429-second");
        when(repository.findPendingUploadReconciliationCandidates(any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(first, second));
        when(repository.findProcessingReconciliationCandidates(any(), any(), any(), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(repository.findProcessingPastRecoveryHorizon(any(), anyInt())).thenReturn(List.of());
        when(inspector.inspect(first.getExternalId()))
                .thenThrow(new MediaAssetProviderException("Cloudinary request failed", new RuntimeException("429")));

        MediaReconciliationService service = new MediaReconciliationService(
                repository,
                persistence,
                inspector,
                new MediaUploadedAssetValidator(new MediaUploadProperties()),
                new MediaUploadProperties(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.runOnce();

        assertEquals(1, result.providerRequests());
        assertEquals(1, result.pendingRowsVisited());
        verify(inspector).inspect(first.getExternalId());
        verify(inspector, never()).inspect(second.getExternalId());
    }

    private static Media pendingUpload(String id) {
        LocalDateTime issued = LocalDateTime.ofInstant(NOW.minusSeconds(7200), ZoneOffset.UTC);
        return Media.createSignedPendingUpload(
                id,
                "product/" + id,
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                "image/png",
                100,
                issued.plusHours(1),
                issued,
                issued.plusHours(1));
    }
}
