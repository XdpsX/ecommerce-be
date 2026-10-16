package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.ArrayList;
import java.util.List;

import com.cloudinary.Transformation;
import com.xdpsx.ecommerce.media.application.storage.MediaVariant;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

/** Centralizes Cloudinary's representation of the approved media preset catalog. */
public final class CloudinaryMediaTransformations {
    private CloudinaryMediaTransformations() {}

    public static List<Transformation<?>> eagerFor(MediaPurpose purpose) {
        List<Transformation<?>> transformations = new ArrayList<>();
        for (MediaVariant variant : MediaVariant.forPurpose(purpose)) {
            transformations.add(forVariant(variant));
        }
        return List.copyOf(transformations);
    }

    public static Transformation<?> forVariant(MediaVariant variant) {
        return switch (variant) {
            case PRODUCT_CARD ->
                auto(new Transformation<>().width(600).height(600).crop("fill").gravity("auto"));
            case PRODUCT_DETAIL ->
                auto(new Transformation<>().width(1200).height(1200).crop("fit"));
            case PRODUCT_CONTENT -> auto(new Transformation<>().width(1200).crop("limit"));
            case CATEGORY_CARD ->
                auto(new Transformation<>().width(600).height(400).crop("fill").gravity("auto"));
            case BRAND_LOGO ->
                auto(new Transformation<>().width(400).height(400).crop("fit"));
        };
    }

    public static List<String> eagerStringsFor(MediaPurpose purpose) {
        return eagerFor(purpose).stream().map(Transformation::generate).toList();
    }

    private static Transformation<?> auto(Transformation<?> transformation) {
        return transformation.quality("auto");
    }
}
