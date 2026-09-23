package com.xdpsx.ecommerce.common.validation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.MediaType;

public class FileConstants {
    public static final Set<String> IMG_TYPES =
            new HashSet<>(Arrays.asList(MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE));

    public static final int BRAND_IMG_WIDTH = 320;
    public static final String BRAND_IMG_FOLDER = "brands";

    public static final int NUMBER_PRODUCT_IMAGES = 5;
    public static final int PRODUCT_IMG_WIDTH = 300;
    public static final String PRODUCT_IMG_FOLDER = "products";
}
