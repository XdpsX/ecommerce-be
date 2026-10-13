package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.media.domain.Media;
import com.xdpsx.ecommerce.media.domain.MediaAttachmentStatus;
import com.xdpsx.ecommerce.media.domain.MediaProcessingStatus;
import com.xdpsx.ecommerce.media.persistence.MediaRepository;

import tools.jackson.databind.json.JsonMapper;

class CloudinaryEagerWebhookHandlerTest {
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final CloudinaryEagerWebhookHandler handler =
            new CloudinaryEagerWebhookHandler(JsonMapper.builder().build(), mediaRepository);

    @Test
    void handle_ShouldMarkProcessingReady_AndAcknowledgeDuplicate() {
        Media media = media(MediaProcessingStatus.PROCESSING);
        when(mediaRepository.findByProcessingReferenceForUpdate(eq("batch-1"))).thenReturn(Optional.of(media));

        String payload = """
                {
                  "notification_type":"eager",
                  "batch_id":"batch-1",
                  "eager":[
                    {
                      "transformation":"c_fill,g_auto,h_600,q_auto,w_600",
                      "secure_url":"https://res.cloudinary.com/demo/image/upload/c_fill,g_auto,h_600,q_auto,w_600/products/item.jpg",
                      "status":"success"
                    }
                  ],
                  "unknown":"ignored"
                }
                """;

        assertEquals(CloudinaryEagerWebhookHandler.Result.APPLIED, handler.handle(payload));
        assertEquals(MediaProcessingStatus.READY, media.getProcessingStatus());
        assertEquals(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, handler.handle(payload));
        assertEquals(MediaProcessingStatus.READY, media.getProcessingStatus());
    }

    @Test
    void handle_ShouldStoreSanitizedBoundedFailureReason() {
        Media media = media(MediaProcessingStatus.PROCESSING);
        when(mediaRepository.findByProcessingReferenceForUpdate(eq("batch-2"))).thenReturn(Optional.of(media));
        String reason = "provider\n" + "x".repeat(600);
        String encodedReason;
        try {
            encodedReason = JsonMapper.builder().build().writeValueAsString(reason);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        handler.handle("""
                {
                  "notification_type":"eager",
                  "batch_id":"batch-2",
                  "eager":[
                    {
                      "transformation":"c_limit,q_auto,w_1200",
                      "status":"failed",
                      "error":%s
                    }
                  ]
                }
                """.formatted(encodedReason));

        assertEquals(MediaProcessingStatus.FAILED, media.getProcessingStatus());
        assertEquals(500, media.getProcessingFailureReason().length());
        assertEquals(' ', media.getProcessingFailureReason().charAt(8));
    }

    @Test
    void handle_ShouldKeepFirstTerminalOutcomeWhenLaterOutcomeConflicts() {
        Media ready = media(MediaProcessingStatus.READY);
        when(mediaRepository.findByProcessingReferenceForUpdate(eq("batch-1"))).thenReturn(Optional.of(ready));

        assertEquals(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, handler.handle("""
                        {"notification_type":"eager","batch_id":"batch-1","status":"failed"}
                        """));
        assertEquals(MediaProcessingStatus.READY, ready.getProcessingStatus());

        Media failed = media(MediaProcessingStatus.FAILED);
        when(mediaRepository.findByProcessingReferenceForUpdate(eq("batch-3"))).thenReturn(Optional.of(failed));

        assertEquals(CloudinaryEagerWebhookHandler.Result.DUPLICATE_OR_CONFLICT, handler.handle("""
                        {"notification_type":"eager","batch_id":"batch-3","status":"success"}
                        """));
        assertEquals(MediaProcessingStatus.FAILED, failed.getProcessingStatus());
    }

    private static Media media(MediaProcessingStatus processingStatus) {
        return Media.builder()
                .id("media-id")
                .externalId("asset-id")
                .url("https://example.test/original.jpg")
                .contentType("image/jpeg")
                .purpose(com.xdpsx.ecommerce.media.domain.MediaPurpose.PRODUCT_IMAGE)
                .attachmentStatus(MediaAttachmentStatus.TEMPORARY)
                .processingStatus(processingStatus)
                .processingReference(processingStatus == MediaProcessingStatus.PROCESSING ? "batch-1" : "batch-1")
                .build();
    }
}
