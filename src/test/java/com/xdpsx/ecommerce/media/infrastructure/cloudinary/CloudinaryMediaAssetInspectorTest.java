package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void inspectProcessing_ShouldFollowDerivedCursorWithinPageBudgetAndUseImageUploadScope() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse firstPage = mock(ApiResponse.class);
        ApiResponse secondPage = mock(ApiResponse.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenReturn(firstPage, secondPage);
        when(firstPage.get("public_id")).thenReturn("product/media-1");
        when(firstPage.get("asset_id")).thenReturn("asset-1");
        when(firstPage.get("resource_type")).thenReturn("image");
        when(firstPage.get("type")).thenReturn("upload");
        when(firstPage.get("derived")).thenReturn(List.of(Map.of("transformation", "c_fill,w_1")));
        doReturn("cursor-1").when(firstPage).get("derived_next_cursor");
        when(secondPage.get("public_id")).thenReturn("product/media-1");
        when(secondPage.get("asset_id")).thenReturn("asset-1");
        when(secondPage.get("resource_type")).thenReturn("image");
        when(secondPage.get("type")).thenReturn("upload");
        when(secondPage.get("derived")).thenReturn(List.of(Map.of("transformation", "c_fit,w_2")));

        var result = new CloudinaryMediaAssetInspector(cloudinary)
                .inspectProcessing("product/media-1", 2, Set.of("c_fit,w_2"));

        assertEquals(2, result.pagesRead());
        assertEquals(Set.of("c_fill,w_1", "c_fit,w_2"), result.generatedTransformations());
        assertTrue(result.inventoryComplete());
        org.mockito.ArgumentCaptor<Map<String, Object>> options = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(api, times(2)).resource(eq("product/media-1"), options.capture());
        assertEquals("image", options.getAllValues().getFirst().get("resource_type"));
        assertEquals("upload", options.getAllValues().getFirst().get("type"));
        assertEquals(100, options.getAllValues().getFirst().get("max_results"));
        assertEquals("cursor-1", options.getAllValues().get(1).get("derived_next_cursor"));
    }

    @Test
    void inspectProcessing_ShouldReportInconclusiveWhenPageBudgetDoesNotExhaustInventory() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        ApiResponse response = mock(ApiResponse.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/media-1"), anyMap())).thenReturn(response);
        when(response.get("public_id")).thenReturn("product/media-1");
        when(response.get("asset_id")).thenReturn("asset-1");
        when(response.get("resource_type")).thenReturn("image");
        when(response.get("type")).thenReturn("upload");
        when(response.get("derived")).thenReturn(List.of(Map.of("transformation", "unrelated")));
        when(response.get("derived_next_cursor")).thenReturn("cursor-more");

        var result = new CloudinaryMediaAssetInspector(cloudinary)
                .inspectProcessing("product/media-1", 1, Set.of("expected"));

        assertEquals(1, result.pagesRead());
        assertFalse(result.inventoryComplete());
    }
}
