package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cloudinary.Transformation;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

class CloudinaryMediaUrlGeneratorTest {
    @Test
    void generateVariants_ShouldExposeOnlyPurposeAppropriateSemanticNames() {
        CloudinaryUploader uploader = mock(CloudinaryUploader.class);
        when(uploader.getFileUrl(eq("products/item"), any(Transformation.class)))
                .thenAnswer(invocation -> "https://cdn.test/" + invocation.getArgument(0) + "/variant");
        CloudinaryMediaUrlGenerator generator = new CloudinaryMediaUrlGenerator(uploader);

        Map<String, String> variants = generator.generateVariants("products/item", MediaPurpose.PRODUCT_IMAGE);

        assertEquals(
                Map.of(
                        "productCard", "https://cdn.test/products/item/variant",
                        "productDetail", "https://cdn.test/products/item/variant"),
                variants);

        ArgumentCaptor<Transformation<?>> transformations = ArgumentCaptor.forClass(Transformation.class);
        verify(uploader, times(2)).getFileUrl(eq("products/item"), transformations.capture());
        assertEquals(2, transformations.getAllValues().size());
        assertEquals(
                "c_fill,g_auto,h_600,q_auto,w_600",
                transformations.getAllValues().get(0).generate());
        assertEquals(
                "c_fit,h_1200,q_auto,w_1200",
                transformations.getAllValues().get(1).generate());
        transformations
                .getAllValues()
                .forEach(transformation -> org.junit.jupiter.api.Assertions.assertFalse(
                        transformation.generate().contains("f_auto")));
    }

    @Test
    void generateVariants_ShouldUseProductContentPresetForDescriptionImages() {
        CloudinaryUploader uploader = mock(CloudinaryUploader.class);
        when(uploader.getFileUrl(eq("products/content"), any(Transformation.class)))
                .thenReturn("https://cdn.test/products/content/variant");
        CloudinaryMediaUrlGenerator generator = new CloudinaryMediaUrlGenerator(uploader);

        Map<String, String> variants =
                generator.generateVariants("products/content", MediaPurpose.PRODUCT_DESCRIPTION_IMAGE);

        assertEquals(Map.of("productContent", "https://cdn.test/products/content/variant"), variants);
        ArgumentCaptor<Transformation<?>> transformation = ArgumentCaptor.forClass(Transformation.class);
        verify(uploader).getFileUrl(eq("products/content"), transformation.capture());
        assertEquals("c_limit,q_auto,w_1200", transformation.getValue().generate());
    }
}
