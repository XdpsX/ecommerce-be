package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.exceptions.ApiException;
import com.xdpsx.ecommerce.media.application.InvalidMediaAssetResponseException;
import com.xdpsx.ecommerce.media.application.MediaAssetInspector.InspectedMediaAsset;
import com.xdpsx.ecommerce.media.application.MediaAssetProviderException;

class CloudinaryMediaAssetInspectorTest {
    @Test
    void inspect_ShouldMapAuthoritativeResourceAndSignedCustomContext() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse response = mock(ApiResponse.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenReturn(response);
        when(response.get("public_id")).thenReturn("product/media-1");
        when(response.get("asset_id")).thenReturn("immutable-asset-id");
        when(response.get("resource_type")).thenReturn("image");
        when(response.get("type")).thenReturn("upload");
        when(response.get("format")).thenReturn("png");
        when(response.get("secure_url")).thenReturn("https://res.cloudinary.com/demo/image/upload/a.png");
        when(response.get("bytes")).thenReturn(2048);
        when(response.get("width")).thenReturn(800);
        when(response.get("height")).thenReturn(600);
        when(response.get("created_at")).thenReturn("2026-10-15T02:01:00Z");
        when(response.get("context"))
                .thenReturn(Map.of("custom", Map.of("media_id", "media-1", "purpose", "PRODUCT_IMAGE")));

        InspectedMediaAsset asset = new CloudinaryMediaAssetInspector(cloudinary).inspect("product/media-1");

        assertEquals("immutable-asset-id", asset.assetId());
        assertEquals("image", asset.resourceType());
        assertEquals("upload", asset.deliveryType());
        assertEquals(2048L, asset.bytes());
        assertEquals(800, asset.width());
        assertEquals(600, asset.height());
        assertEquals("media-1", asset.context().get("media_id"));
        assertEquals("PRODUCT_IMAGE", asset.context().get("purpose"));
        verify(api).resource(eq("product/media-1"), anyMap());
    }

    @Test
    void inspect_ShouldTreatProviderNotFoundAsNotYetVisible() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenThrow(new ApiException("Resource not found"));

        assertNull(new CloudinaryMediaAssetInspector(cloudinary).inspect("product/media-1"));
    }

    @Test
    void inspect_ShouldWrapOtherProviderFailuresAsRetryableInspectionFailures() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenThrow(new ApiException("Rate limited"));

        assertThrows(MediaAssetProviderException.class, () -> new CloudinaryMediaAssetInspector(cloudinary)
                .inspect("product/media-1"));
    }

    @Test
    void inspect_ShouldClassifySuccessfulResponseMissingAuthoritativeMetadataAsInvalid() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse response = mock(ApiResponse.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenReturn(response);
        when(response.get("bytes")).thenReturn(null);

        assertThrows(InvalidMediaAssetResponseException.class, () -> new CloudinaryMediaAssetInspector(cloudinary)
                .inspect("product/media-1"));
    }
}
