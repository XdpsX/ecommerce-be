package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.unit.DataSize;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.config.MediaUploadProperties;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;

@ExtendWith(MockitoExtension.class)
class MediaUploadIntentServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    @Mock
    private MediaUploadIntentPersistence persistence;

    @Mock
    private MediaUploadAuthorizer authorizer;

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
                persistence, authorizer, currentUserIdProvider, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void create_ShouldPersistOwnedPendingIntentWithBoundedPolicySnapshot() {
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(42L);
        LocalDateTime issuedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        when(authorizer.authorize(any()))
                .thenReturn(new MediaUploadAuthorizer.UploadAuthorization(
                        "https://api.cloudinary.com/v1_1/test/image/upload",
                        java.util.Map.of("api_key", "public", "signature", "signed"),
                        issuedAt,
                        issuedAt.plusHours(1)));
        UploadIntentView result = service.create(
                new CreateUploadIntentCommand(MediaPurpose.PRODUCT_IMAGE, " front.png ", "image/png", 182_340));

        InOrder order = inOrder(authorizer, persistence);
        order.verify(authorizer)
                .authorize(argThat(request -> request.purpose() == MediaPurpose.PRODUCT_IMAGE
                        && request.publicId().equals("product/" + result.mediaId())));
        order.verify(persistence)
                .persist(
                        eq(result.mediaId()),
                        eq("product/" + result.mediaId()),
                        eq(42L),
                        eq(MediaPurpose.PRODUCT_IMAGE),
                        eq("image/png"),
                        eq(182_340L),
                        any(LocalDateTime.class),
                        eq(issuedAt),
                        eq(issuedAt.plusHours(1)));
        assertEquals(MediaUploadStatus.PENDING_UPLOAD, result.uploadStatus());
        assertEquals(MediaProcessingStatus.PENDING, result.processingStatus());
        assertEquals(MediaAttachmentStatus.TEMPORARY, result.attachmentStatus());
        assertEquals("product/" + result.mediaId(), result.publicId());
        assertEquals("signed", result.uploadFields().get("signature"));
        assertEquals("https://api.cloudinary.com/v1_1/test/image/upload", result.uploadUrl());
    }

    @Test
    void create_ShouldRejectOversizedFileBeforePersistence() {
        CreateUploadIntentCommand command = new CreateUploadIntentCommand(
                MediaPurpose.PRODUCT_IMAGE,
                "large.png",
                "image/png",
                DataSize.ofMegabytes(2).toBytes() + 1);

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.create(command));
        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getCode());
        assertEquals("sizeBytes", exception.getParameters().get("field"));
        verifyNoInteractions(persistence, authorizer);
        verifyNoInteractions(currentUserIdProvider);
    }

    @Test
    void create_ShouldNotPersistIntentWhenSigningFails() {
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(42L);
        when(authorizer.authorize(any())).thenThrow(new IllegalStateException("signing failed"));

        assertThrows(
                IllegalStateException.class,
                () -> service.create(
                        new CreateUploadIntentCommand(MediaPurpose.PRODUCT_IMAGE, "image.png", "image/png", 1024)));

        verify(authorizer).authorize(any());
        verifyNoInteractions(persistence);
    }
}
