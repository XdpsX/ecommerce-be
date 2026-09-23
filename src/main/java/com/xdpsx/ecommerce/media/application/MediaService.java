package com.xdpsx.ecommerce.media.application;

import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.MediaResourceType;

public interface MediaService {
    ViewMediaDTO createMedia(CreateMediaDTO request, MediaResourceType resourceType);

    void deleteMedia(String id);
}
