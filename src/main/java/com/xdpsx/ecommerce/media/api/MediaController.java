package com.xdpsx.ecommerce.media.api;

import java.time.ZoneOffset;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaUploadIntentRequest;
import com.xdpsx.ecommerce.media.api.dto.MediaDirectStatusResponse;
import com.xdpsx.ecommerce.media.api.dto.MediaUploadIntentResponse;
import com.xdpsx.ecommerce.media.api.dto.UploadedMediaDTO;
import com.xdpsx.ecommerce.media.application.CreateUploadIntentCommand;
import com.xdpsx.ecommerce.media.application.MediaDirectUploadService;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.media.application.MediaUploadIntentService;
import com.xdpsx.ecommerce.media.application.UploadIntentView;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MediaController implements MediaApi {
    private final MediaService mediaService;
    private final MediaUploadIntentService mediaUploadIntentService;
    private final MediaDirectUploadService mediaDirectUploadService;

    @PostMapping(path = "/upload-intents", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadIntentResponse createUploadIntent(@Valid @RequestBody CreateMediaUploadIntentRequest request) {
        UploadIntentView view = mediaUploadIntentService.create(new CreateUploadIntentCommand(
                request.purpose(), request.filename(), request.contentType(), request.sizeBytes()));
        return new MediaUploadIntentResponse(
                view.mediaId(),
                view.intentExpiresAt().toInstant(ZoneOffset.UTC),
                view.uploadAuthorizationExpiresAt().toInstant(ZoneOffset.UTC),
                new MediaUploadIntentResponse.UploadInstructions(
                        "CLOUDINARY", "POST", view.uploadUrl(), view.uploadFields()),
                new MediaUploadIntentResponse.Status(
                        view.uploadStatus(), view.processingStatus(), view.attachmentStatus()));
    }

    @PostMapping("/{id}/complete")
    public MediaDirectStatusResponse completeUpload(@PathVariable String id) {
        return mediaDirectUploadService.complete(id);
    }

    @GetMapping("/{id}")
    public MediaDirectStatusResponse getMediaStatus(@PathVariable String id) {
        return mediaDirectUploadService.getStatus(id);
    }

    @PostMapping(path = "/image-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedMediaDTO createMedia(@RequestParam String resource, @Valid @ModelAttribute CreateMediaDTO request) {
        MediaPurpose purpose = MediaPurpose.fromResource(resource);
        if (purpose == null) {
            throw new ApplicationException(ErrorCode.INVALID_MEDIA_RESOURCE_TYPE, Map.of("resource", resource));
        }
        return mediaService.createMedia(request, purpose);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable String id) {
        mediaDirectUploadService.delete(id);
    }
}
