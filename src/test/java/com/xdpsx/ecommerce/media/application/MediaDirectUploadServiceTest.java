package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadPersistence.Snapshot;
import com.xdpsx.ecommerce.media.application.storage.MediaUrlGenerator;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

@ExtendWith(MockitoExtension.class)
class MediaDirectUploadServiceTest {
    private static final LocalDateTime ISSUED = LocalDateTime.of(2026, 10, 15, 2, 0);
    private static final LocalDateTime EXPIRES = ISSUED.plusHours(1);

    @Mock
    private MediaDirectUploadPersistence persistence;

    @Mock
    private MediaAssetInspector inspector;

    @Mock
    private CurrentUserIdProvider userIdProvider;

    @Mock
    private MediaUrlGenerator urlGenerator;

    private MediaDirectUploadService service;

    @BeforeEach
    void setUp() {
        service = new MediaDirectUploadService(
                persistence,
                inspector,
                userIdProvider,
                new MediaUploadProperties(),
                urlGenerator,
                Clock.fixed(Instant.parse("2026-10-15T02:30:00Z"), ZoneOffset.UTC));
        when(userIdProvider.getRequiredUserId()).thenReturn(42L);
    }

    @Test
    void complete_ShouldVerifyStoredIdentityAndPersistAuthoritativeProviderMetadata() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        Snapshot uploaded = snapshot(MediaUploadStatus.UPLOADED, MediaAttachmentStatus.TEMPORARY, "asset-1");
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1")).thenReturn(asset("product/media-1", "asset-1", "png", 800));
        when(persistence.finalizeUpload(pending, 42L, asset("product/media-1", "asset-1", "png", 800), "image/png"))
                .thenReturn(uploaded);

        var result = service.complete("media-1");

        assertEquals(MediaUploadStatus.UPLOADED, result.uploadStatus());
        assertEquals("https://cdn.example.test/asset.png", result.originalUrl());
        verify(inspector).inspect("product/media-1");
        verify(persistence).finalizeUpload(pending, 42L, asset("product/media-1", "asset-1", "png", 800), "image/png");
    }

    @Test
    void complete_ShouldClaimPermanentPolicyMismatchWithoutFinalizing() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1")).thenReturn(asset("attacker/other-id", "asset-1", "png", 800));

        assertThrows(ApplicationException.class, () -> service.complete("media-1"));

        verify(persistence).reject(pending, 42L, "Cloudinary asset does not satisfy the signed upload policy");
        verify(persistence, never()).finalizeUpload(any(), any(), any(), any());
    }

    @Test
    void complete_ShouldPermanentlyRejectSuccessfulProviderResponseWithoutRequiredMetadata() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1")).thenThrow(new InvalidMediaAssetResponseException("missing bytes"));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.complete("media-1"));

        assertEquals(com.xdpsx.ecommerce.common.error.ErrorCode.MEDIA_ASSET_INVALID, exception.getCode());
        verify(persistence).reject(pending, 42L, "Cloudinary asset response omitted required metadata");
        verify(persistence, never()).finalizeUpload(any(), any(), any(), any());
    }

    @Test
    void complete_ShouldKeepIntentRetryableWhenProviderIsUnavailable() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1"))
                .thenThrow(new MediaAssetProviderException("provider unavailable", new RuntimeException()));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.complete("media-1"));

        assertEquals(com.xdpsx.ecommerce.common.error.ErrorCode.MEDIA_PROVIDER_UNAVAILABLE, exception.getCode());
        verify(persistence, never()).reject(any(), any(), any());
    }

    @Test
    void complete_ShouldBeIdempotentWithoutCallingProviderAgain() {
        Snapshot uploaded = snapshot(MediaUploadStatus.UPLOADED, MediaAttachmentStatus.TEMPORARY, "asset-1");
        when(persistence.snapshot("media-1", 42L)).thenReturn(uploaded);

        var result = service.complete("media-1");

        assertEquals(MediaUploadStatus.UPLOADED, result.uploadStatus());
        verifyNoInteractions(inspector);
        verify(persistence, never()).finalizeUpload(any(), any(), any(), any());
    }

    @Test
    void complete_ShouldClaimExpiredIntentAndRejectAfterCompletionGrace() {
        Snapshot expired = new Snapshot(
                "media-1",
                "product/media-1",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                MediaUploadStatus.PENDING_UPLOAD,
                MediaProcessingStatus.PENDING,
                MediaAttachmentStatus.TEMPORARY,
                null,
                null,
                EXPIRES,
                ISSUED,
                EXPIRES.minusMinutes(41));
        when(persistence.snapshot("media-1", 42L)).thenReturn(expired);

        assertThrows(ApplicationException.class, () -> service.complete("media-1"));

        verify(persistence).claimExpired("media-1", 42L);
        verifyNoInteractions(inspector);
    }

    @Test
    void complete_ShouldAcceptAssetCreatedAtAuthorizationExpiry() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        Snapshot uploaded = snapshot(MediaUploadStatus.UPLOADED, MediaAttachmentStatus.TEMPORARY, "asset-at-expiry");
        InspectedMediaAsset atExpiry = asset("product/media-1", "asset-at-expiry", "png", 800, EXPIRES);
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1")).thenReturn(atExpiry);
        when(persistence.finalizeUpload(pending, 42L, atExpiry, "image/png")).thenReturn(uploaded);

        assertEquals(MediaUploadStatus.UPLOADED, service.complete("media-1").uploadStatus());
        verify(persistence, never()).reject(any(), any(), any());
    }

    @Test
    void complete_ShouldRejectAssetCreatedAfterAuthorizationExpiryDespiteCompletionGrace() {
        Snapshot pending = snapshot(MediaUploadStatus.PENDING_UPLOAD, MediaAttachmentStatus.TEMPORARY, null);
        InspectedMediaAsset afterExpiry =
                asset("product/media-1", "asset-after-expiry", "png", 800, EXPIRES.plusSeconds(1));
        when(persistence.snapshot("media-1", 42L)).thenReturn(pending);
        when(inspector.inspect("product/media-1")).thenReturn(afterExpiry);

        assertThrows(ApplicationException.class, () -> service.complete("media-1"));
        verify(persistence).reject(pending, 42L, "Cloudinary asset does not satisfy the signed upload policy");
        verify(persistence, never()).finalizeUpload(any(), any(), any(), any());
    }

    private static Snapshot snapshot(MediaUploadStatus upload, MediaAttachmentStatus attachment, String assetId) {
        return new Snapshot(
                "media-1",
                "product/media-1",
                42L,
                MediaPurpose.PRODUCT_IMAGE,
                upload,
                upload == MediaUploadStatus.UPLOADED ? MediaProcessingStatus.PROCESSING : MediaProcessingStatus.PENDING,
                attachment,
                upload == MediaUploadStatus.UPLOADED ? "https://cdn.example.test/asset.png" : null,
                assetId,
                EXPIRES.plusHours(1),
                ISSUED,
                EXPIRES);
    }

    private static InspectedMediaAsset asset(String publicId, String assetId, String format, int width) {
        return asset(publicId, assetId, format, width, ISSUED.plusMinutes(1));
    }

    private static InspectedMediaAsset asset(
            String publicId, String assetId, String format, int width, LocalDateTime createdAt) {
        return new InspectedMediaAsset(
                publicId,
                assetId,
                "image",
                "upload",
                format,
                "https://cdn.example.test/asset.png",
                1234,
                width,
                600,
                createdAt,
                Map.of("media_id", "media-1", "purpose", "PRODUCT_IMAGE"));
    }
}
