package com.xdpsx.ecommerce.controllers;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.constants.messages.EMessage;
import com.xdpsx.ecommerce.constants.messages.SMessage;
import com.xdpsx.ecommerce.controllers.docs.MediaApiDocs;
import com.xdpsx.ecommerce.dtos.common.APIResponse;
import com.xdpsx.ecommerce.dtos.media.CreateMediaDTO;
import com.xdpsx.ecommerce.dtos.media.ViewMediaDTO;
import com.xdpsx.ecommerce.entities.enums.MediaResourceType;
import com.xdpsx.ecommerce.exceptions.BadRequestException;
import com.xdpsx.ecommerce.services.MediaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MediaController implements MediaApiDocs {
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
