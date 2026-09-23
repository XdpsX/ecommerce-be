package com.xdpsx.ecommerce.services;

import com.xdpsx.ecommerce.dtos.media.CreateMediaDTO;
import com.xdpsx.ecommerce.dtos.media.ViewMediaDTO;
import com.xdpsx.ecommerce.entities.enums.MediaResourceType;

public interface MediaService {
    ViewMediaDTO createMedia(CreateMediaDTO request, MediaResourceType resourceType);

    void deleteMedia(String id);
}
