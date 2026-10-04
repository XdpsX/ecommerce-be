package com.xdpsx.ecommerce.catalog.brand.domain;

import com.xdpsx.ecommerce.media.domain.Media;

public interface HasImage {
    Media getImage();

    void setImage(Media image);
}
