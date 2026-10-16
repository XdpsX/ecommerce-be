package com.xdpsx.ecommerce.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.auth.application.CurrentUserIdProvider;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaOwnerType;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;
import com.xdpsx.ecommerce.media.domain.MediaUploadStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

@ExtendWith(MockitoExtension.class)
class MediaAttachmentServiceTest {
    @Mock
    private MediaRepository repository;

    @Mock
    private CurrentUserIdProvider currentUserIdProvider;

    private MediaAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new MediaAttachmentService(
                repository, currentUserIdProvider, mock(jakarta.persistence.EntityManager.class));
    }

    @Test
    void synchronizeAttachments_ShouldBindOwnerAndRetainIdempotently() {
        when(currentUserIdProvider.getRequiredUserId()).thenReturn(42L);
        Media media = temporary("m-1", MediaPurpose.PRODUCT_IMAGE, 42L);
        when(repository.findAllByIdInForUpdate(List.of("m-1"))).thenReturn(List.of(media));

        List<Media> attached = service.synchronizeAttachments(
                new MediaOwner(MediaOwnerType.PRODUCT, "99"), MediaPurpose.PRODUCT_IMAGE, List.of(), List.of("m-1"));

        assertThat(attached).containsExactly(media);
        assertThat(media.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.ACTIVE);
        assertThat(media.getAttachmentOwnerType()).isEqualTo(MediaOwnerType.PRODUCT);
        assertThat(media.getAttachmentOwnerId()).isEqualTo("99");

        when(repository.findAllByIdInForUpdate(List.of("m-1"))).thenReturn(List.of(media));
        service.synchronizeAttachments(
                new MediaOwner(MediaOwnerType.PRODUCT, "99"),
                MediaPurpose.PRODUCT_IMAGE,
                List.of("m-1"),
                List.of("m-1"));
        assertThat(media.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.ACTIVE);
    }

    @Test
    void synchronizeAttachments_ShouldRejectDifferentOwnerAndLegacyUnboundActiveMedia() {
        Media attached = temporary("m-2", MediaPurpose.BRAND_LOGO, 42L);
        attached.attach(MediaOwnerType.BRAND, "1");
        when(repository.findAllByIdInForUpdate(List.of("m-2"))).thenReturn(List.of(attached));

        ApplicationException crossOwner = assertThrows(
                ApplicationException.class,
                () -> service.synchronizeAttachments(
                        new MediaOwner(MediaOwnerType.BRAND, "2"),
                        MediaPurpose.BRAND_LOGO,
                        List.of("m-2"),
                        List.of("m-2")));
        assertThat(crossOwner.getCode()).isEqualTo(ErrorCode.MEDIA_ATTACHMENT_CONFLICT);

        Media legacy = Media.builder()
                .id("legacy")
                .externalId("legacy-public")
                .url("https://cdn.example.test/legacy.png")
                .contentType("image/png")
                .purpose(MediaPurpose.BRAND_LOGO)
                .attachmentStatus(MediaAttachmentStatus.ACTIVE)
                .uploadStatus(MediaUploadStatus.UPLOADED)
                .build();
        when(repository.findAllByIdInForUpdate(List.of("legacy"))).thenReturn(List.of(legacy));
        ApplicationException unbound = assertThrows(
                ApplicationException.class,
                () -> service.synchronizeAttachments(
                        new MediaOwner(MediaOwnerType.BRAND, "1"),
                        MediaPurpose.BRAND_LOGO,
                        List.of("legacy"),
                        List.of("legacy")));
        assertThat(unbound.getCode()).isEqualTo(ErrorCode.MEDIA_ATTACHMENT_CONFLICT);
        assertThat(legacy.getAttachmentOwnerId()).isNull();
    }

    @Test
    void synchronizeAttachments_ShouldRejectPendingAndForeignUploaderWithoutPartialMutation() {
        Media first = temporary("a", MediaPurpose.PRODUCT_IMAGE, 42L);
        Media pending = Media.createPendingUpload(
                "b", "product/b", 42L, MediaPurpose.PRODUCT_IMAGE, "image/png", 100, java.time.LocalDateTime.now());
        when(repository.findAllByIdInForUpdate(List.of("a", "b"))).thenReturn(List.of(first, pending));

        assertThrows(
                ApplicationException.class,
                () -> service.synchronizeAttachments(
                        new MediaOwner(MediaOwnerType.PRODUCT, "4"),
                        MediaPurpose.PRODUCT_IMAGE,
                        List.of(),
                        List.of("a", "b")));
        assertThat(first.getAttachmentStatus()).isEqualTo(MediaAttachmentStatus.TEMPORARY);
        assertThat(first.getAttachmentOwnerId()).isNull();
    }

    private static Media temporary(String id, MediaPurpose purpose, Long uploaderId) {
        return Media.createUploaded(
                id,
                "public/" + id,
                "https://cdn.example.test/" + id + ".png",
                null,
                "image/png",
                purpose,
                uploaderId,
                MediaAttachmentStatus.TEMPORARY,
                "batch-" + id);
    }
}
