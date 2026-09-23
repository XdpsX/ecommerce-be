package com.xdpsx.ecommerce.media.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.api.APIResponse;
import com.xdpsx.ecommerce.common.error.BadRequestException;
import com.xdpsx.ecommerce.common.error.EMessage;
import com.xdpsx.ecommerce.common.error.SMessage;
import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.application.MediaService;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MediaController implements MediaControllerApi {
    private final MediaService mediaService;

    @PostMapping(path = "/media/image-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public APIResponse<ViewMediaDTO> createMedia(
            @RequestParam String resource, @Valid @ModelAttribute CreateMediaDTO request) {
        MediaResourceType resourceType = MediaResourceType.fromResource(resource);
        if (resourceType == null) {
            throw new BadRequestException(EMessage.INVALID_RESOURCE_TYPE, resource);
        }
        ViewMediaDTO data = mediaService.createMedia(request, resourceType);
        return new APIResponse<>(HttpStatus.CREATED, data, SMessage.CREATE_SUCCESSFULLY);
    }

    @DeleteMapping("/media/{id}")
    public APIResponse<Void> deleteMedia(@PathVariable String id) {
        mediaService.deleteMedia(id);
        return APIResponse.noContent(SMessage.DELETE_SUCCESSFULLY);
    }
}
