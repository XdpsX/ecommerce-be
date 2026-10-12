package com.xdpsx.ecommerce.media.api;

import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Cloudinary Webhook API")
interface CloudinaryWebhookApi {
    @Operation(
            summary = "Receive Cloudinary eager notification",
            responses = {
                @ApiResponse(responseCode = "200", description = "Notification acknowledged"),
                @ApiResponse(responseCode = "400", description = "Malformed notification"),
                @ApiResponse(responseCode = "401", description = "Invalid signature"),
                @ApiResponse(responseCode = "503", description = "Referenced media is not visible yet")
            })
    ResponseEntity<Void> eagerNotification(String signature, String timestamp, byte[] body);
}
