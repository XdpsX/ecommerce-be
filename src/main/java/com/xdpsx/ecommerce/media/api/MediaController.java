package com.xdpsx.ecommerce.media.api;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MediaController implements MediaControllerApi {
    private final MediaService mediaService;

    @PostMapping(path = "/media/image-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ViewMediaDTO createMedia(@RequestParam String resource, @Valid @ModelAttribute CreateMediaDTO request) {
        MediaPurpose purpose = MediaPurpose.fromResource(resource);
        if (purpose == null) {
            throw new ApplicationException(ErrorCode.INVALID_MEDIA_RESOURCE_TYPE, Map.of("resource", resource));
        }
        return mediaService.createMedia(request, purpose);
    }

    @DeleteMapping("/media/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@PathVariable String id) {
        mediaService.deleteMedia(id);
    }
}
