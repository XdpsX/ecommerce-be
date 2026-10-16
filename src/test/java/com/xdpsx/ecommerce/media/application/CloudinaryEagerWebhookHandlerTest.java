package com.xdpsx.ecommerce.media.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.xdpsx.ecommerce.media.infrastructure.cloudinary.CloudinaryEagerOutcomeEvaluator;

import tools.jackson.databind.json.JsonMapper;

class CloudinaryEagerWebhookHandlerTest {
    private final CloudinaryWebhookPersistence persistence = mock(CloudinaryWebhookPersistence.class);
    private final CloudinaryEagerOutcomeEvaluator evaluator = mock(CloudinaryEagerOutcomeEvaluator.class);
    private final CloudinaryBatchReferenceBinder binder = mock(CloudinaryBatchReferenceBinder.class);
    private final CloudinaryEagerWebhookHandler handler =
            new CloudinaryEagerWebhookHandler(JsonMapper.builder().build(), persistence, evaluator, binder);

    @Test
    void handle_ShouldRoutePublicIdentityToTransactionalMergeAndBindBatchAfterCommit() {
        String payload = """
                {
                  "notification_type":"eager",
                  "batch_id":"batch-1",
                  "public_id":"product/media-id",
                  "asset_id":"immutable-asset-id",
                  "eager":[]
                }
                """;
        when(persistence.merge(any(), eq(evaluator)))
                .thenReturn(new CloudinaryWebhookPersistence.MergeResult(
                        CloudinaryEagerWebhookHandler.Result.APPLIED, "media-id", "batch-1"));

        assertEquals(CloudinaryEagerWebhookHandler.Result.APPLIED, handler.handle(payload));

        verify(persistence).merge(any(), eq(evaluator));
        verify(binder).bindIfAbsent("media-id", "batch-1");
    }

    @Test
    void handle_ShouldRejectMalformedAndMissingIdentityPayloads() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> handler.handle("not-json"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> handler.handle("{\"notification_type\":\"eager\",\"eager\":[]}"));
    }
}
