package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class MediaUploadIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private CurrentUserIdProvider currentUserIdProvider;

    private MediaUploadProperties properties;
    private MediaUploadIntentService service;

    @BeforeEach
    void setUp() {
        properties = new MediaUploadProperties();
        properties.setIntentLifetime(Duration.ofHours(1));
        properties.setMaxFileSize(DataSize.ofMegabytes(2));
        service = new MediaUploadIntentService(
                mediaRepository, currentUserIdProvider, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void create_ShouldPersistOwnedPendingIntentWithBoundedPolicySnapshot() {
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(42L);
        when(mediaRepository.save(any(Media.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UploadIntentView result = service.create(
                new CreateUploadIntentCommand(MediaPurpose.PRODUCT_IMAGE, " front.png ", "image/png", 182_340));

        ArgumentCaptor<Media> captor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(captor.capture());
        Media saved = captor.getValue();
        assertEquals(42L, saved.getUploadedByUserId());
        assertEquals(MediaPurpose.PRODUCT_IMAGE, saved.getPurpose());
        assertEquals("image/png", saved.getExpectedContentType());
        assertEquals(182_340L, saved.getExpectedSizeBytes());
        assertEquals(NOW.plus(Duration.ofHours(1)), saved.getIntentExpiresAt().toInstant(ZoneOffset.UTC));
        assertEquals(MediaUploadStatus.PENDING_UPLOAD, result.uploadStatus());
        assertEquals(MediaProcessingStatus.PENDING, result.processingStatus());
        assertEquals(MediaAttachmentStatus.TEMPORARY, result.attachmentStatus());
        assertEquals("product/" + result.mediaId(), result.publicId());
        assertNull(saved.getUrl());
        assertNull(saved.getContentType());
    }

    @Test
    void create_ShouldRejectOversizedFileBeforePersistence() {
        CreateUploadIntentCommand command = new CreateUploadIntentCommand(
                MediaPurpose.PRODUCT_IMAGE,
                "large.png",
                "image/png",
                DataSize.ofMegabytes(2).toBytes() + 1);

        assertThrows(IllegalArgumentException.class, () -> service.create(command));
        verify(mediaRepository, never()).save(any());
        verifyNoInteractions(currentUserIdProvider);
    }
}
