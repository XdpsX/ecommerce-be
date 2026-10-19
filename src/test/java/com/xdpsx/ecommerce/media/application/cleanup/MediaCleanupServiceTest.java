package com.xdpsx.ecommerce.media.application.cleanup;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.config.MediaCleanupProperties;
import com.xdpsx.ecommerce.config.MediaUploadProperties;

@ExtendWith(MockitoExtension.class)
class MediaCleanupServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Mock
    private MediaCleanupPersistence persistence;

    @Mock
    private MediaCleanupProvider provider;

    private MediaCleanupProperties cleanupProperties;
    private MediaUploadProperties uploadProperties;
    private MediaCleanupService service;

    @BeforeEach
    void setUp() {
        cleanupProperties = new MediaCleanupProperties();
        cleanupProperties.setBatchSize(10);
        cleanupProperties.setProviderRequestBudget(30);
        uploadProperties = new MediaUploadProperties();
        service = new MediaCleanupService(
                persistence, provider, cleanupProperties, uploadProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void runCleanup_ShouldRetainSignedTombstoneAndScheduleReinspectionEvenWhenAbsent() {
        var target = new MediaCleanupPersistence.CleanupSnapshot("signed", "product/signed", "asset-1", true);
        when(persistence.candidates(any(), any(), any(), eq(10))).thenReturn(List.of(target));
        when(persistence.prepare(eq(target), any(), any(), any())).thenReturn(target);
        when(provider.inspectExactPublicId(target.publicId(), MediaCleanupProvider.CleanupScope.BACKEND_ISSUED))
                .thenReturn(new MediaCleanupProvider.Inspection(List.of()));

        service.runCleanup();

        verify(provider).inspectExactPublicId("product/signed", MediaCleanupProvider.CleanupScope.BACKEND_ISSUED);
        verify(persistence)
                .finish(
                        target,
                        true,
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusHours(24));
        verify(persistence, never()).retry(any(), any());
    }

    @Test
    void runCleanup_ShouldDeleteExactAssetsAndRetainSignedTombstone() {
        var target = new MediaCleanupPersistence.CleanupSnapshot("signed", "product/signed", "asset-1", true);
        var asset = new MediaCleanupProvider.AssetIdentity("product/signed", "asset-1", "video", "upload");
        when(persistence.candidates(any(), any(), any(), eq(10))).thenReturn(List.of(target));
        when(persistence.prepare(eq(target), any(), any(), any())).thenReturn(target);
        when(provider.inspectExactPublicId(target.publicId(), MediaCleanupProvider.CleanupScope.BACKEND_ISSUED))
                .thenReturn(new MediaCleanupProvider.Inspection(List.of(asset)));

        service.runCleanup();

        verify(provider).deleteExactAssets(List.of(asset));
        verify(persistence).finish(eq(target), eq(true), any(LocalDateTime.class));
    }

    @Test
    void runCleanup_ShouldRetryProviderFailureAndStopTickOnRateLimit() {
        var first = new MediaCleanupPersistence.CleanupSnapshot("one", "product/one", null, false);
        var second = new MediaCleanupPersistence.CleanupSnapshot("two", "product/two", null, false);
        when(persistence.candidates(any(), any(), any(), eq(10))).thenReturn(List.of(first, second));
        when(persistence.prepare(eq(first), any(), any(), any())).thenReturn(first);
        when(provider.inspectExactPublicId(first.publicId(), MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY))
                .thenThrow(new MediaCleanupProviderException("Cloudinary rate limit 429"));

        service.runCleanup();

        verify(persistence)
                .retry(first, LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusMinutes(15));
        verify(provider, never())
                .inspectExactPublicId(second.publicId(), MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY);
        verify(persistence, never()).prepare(eq(second), any(), any(), any());
    }

    @Test
    void runCleanup_ShouldRespectProviderRequestBudget() {
        var first = new MediaCleanupPersistence.CleanupSnapshot("one", "product/one", null, false);
        var second = new MediaCleanupPersistence.CleanupSnapshot("two", "product/two", null, false);
        cleanupProperties.setProviderRequestBudget(2);
        when(persistence.candidates(any(), any(), any(), eq(10))).thenReturn(List.of(first, second));
        when(persistence.prepare(eq(first), any(), any(), any())).thenReturn(first);
        when(provider.inspectExactPublicId(first.publicId(), MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY))
                .thenReturn(new MediaCleanupProvider.Inspection(List.of()));

        service.runCleanup();

        verify(provider).inspectExactPublicId(first.publicId(), MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY);
        verify(provider, never())
                .inspectExactPublicId(second.publicId(), MediaCleanupProvider.CleanupScope.LEGACY_IMAGE_ONLY);
    }
}
