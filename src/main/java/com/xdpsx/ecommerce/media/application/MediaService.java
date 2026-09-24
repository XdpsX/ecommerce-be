package com.xdpsx.ecommerce.media.application;

import com.xdpsx.ecommerce.media.api.dto.CreateMediaDTO;
import com.xdpsx.ecommerce.media.api.dto.ViewMediaDTO;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public interface MediaService {
    ViewMediaDTO createMedia(CreateMediaDTO request, MediaPurpose purpose);

    void deleteMedia(String id);
}
