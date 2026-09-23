package com.xdpsx.ecommerce.media.domain;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.cloudinary.Transformation;

public enum MediaResourceType {
    CATEGORY("category", "categories", 280),
    BRAND("brand", "brands", 280),
    PRODUCT("product", "products", 560),
    ;

    private final String resource;
    private final String folder;
    private final Integer minWidth;

    MediaResourceType(String resource, String folder, Integer minWidth) {
        this.resource = resource;
        this.folder = folder;
        this.minWidth = minWidth;
    }

    public String resource() {
        return this.resource;
    }

    public String folder() {
        return this.folder;
    }

    public Integer minWidth() {
        return this.minWidth;
    }

    public Map<String, Object> getUploadOptions() {
        Map<String, Object> options = new HashMap<>();

        if (folder != null) {
            options.put("folder", folder);
        }

        if (minWidth != null) {
            options.put("transformation", new Transformation<>().width(minWidth).crop("scale"));
        }

        return options;
    }

    public static MediaResourceType fromResource(String resource) {
        return Arrays.stream(MediaResourceType.values())
                .filter(type -> type.resource.equalsIgnoreCase(resource))
                .findFirst()
                .orElse(null);
        //                .orElseThrow(() -> new BadRequestException("Invalid media resource type: " + resource));
    }
}
