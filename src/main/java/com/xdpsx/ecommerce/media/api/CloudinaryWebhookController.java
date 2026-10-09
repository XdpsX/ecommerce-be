package com.xdpsx.ecommerce.media.api;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.media.application.CloudinaryEagerWebhookHandler;
import com.xdpsx.ecommerce.media.infrastructure.cloudinary.NotificationSignatureVerifier;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CloudinaryWebhookController {
    public static final String EAGER_PATH = "/webhooks/cloudinary/eager";

    private final NotificationSignatureVerifier signatureVerifier;
    private final CloudinaryEagerWebhookHandler webhookHandler;

    @PostMapping(EAGER_PATH)
    public ResponseEntity<Void> eagerNotification(
            @RequestHeader(name = "X-Cld-Signature", required = false) String signature,
            @RequestHeader(name = "X-Cld-Timestamp", required = false) String timestamp,
            @RequestBody byte[] body) {
        String rawBody = new String(body, StandardCharsets.UTF_8);
        if (!signatureVerifier.isValid(signature, timestamp, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        final CloudinaryEagerWebhookHandler.Result result;
        try {
            result = webhookHandler.handle(rawBody);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
        if (result == CloudinaryEagerWebhookHandler.Result.NOT_VISIBLE_YET) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok().build();
    }
}
