package com.xdpsx.ecommerce.media.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.xdpsx.ecommerce.media.application.CloudinaryEagerWebhookHandler;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.NotificationSignatureVerifier;

class CloudinaryWebhookControllerTest {
    private final NotificationSignatureVerifier signatureVerifier = mock(NotificationSignatureVerifier.class);
    private final CloudinaryEagerWebhookHandler webhookHandler = mock(CloudinaryEagerWebhookHandler.class);
    private final CloudinaryWebhookController controller =
            new CloudinaryWebhookController(signatureVerifier, webhookHandler);

    @Test
    void eagerNotification_ShouldRejectInvalidSignatureBeforeCallingHandler() {
        when(signatureVerifier.isValid("bad", "123", "{}")).thenReturn(false);

        var response = controller.eagerNotification("bad", "123", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(webhookHandler, never()).handle(anyString());
    }

    @Test
    void eagerNotification_ShouldReturnBadRequestForMalformedSignedPayload() {
        when(signatureVerifier.isValid("valid", "123", "not-json")).thenReturn(true);
        when(webhookHandler.handle("not-json")).thenThrow(new IllegalArgumentException("malformed"));

        var response = controller.eagerNotification("valid", "123", "not-json".getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void eagerNotification_ShouldRequestRetryWhenMediaIsNotVisibleYet() {
        when(signatureVerifier.isValid("valid", "123", "{}")).thenReturn(true);
        when(webhookHandler.handle("{}")).thenReturn(CloudinaryEagerWebhookHandler.Result.NOT_VISIBLE_YET);

        var response = controller.eagerNotification("valid", "123", "{}".getBytes(StandardCharsets.UTF_8));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }
}
