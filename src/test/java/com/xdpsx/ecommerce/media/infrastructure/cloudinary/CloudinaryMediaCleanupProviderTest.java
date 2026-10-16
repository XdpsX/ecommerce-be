package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.api.exceptions.ApiException;
import com.xdpsx.ecommerce.media.application.MediaCleanupProvider.CleanupScope;
import com.xdpsx.ecommerce.media.application.MediaCleanupProviderException;

class CloudinaryMediaCleanupProviderTest {
    @Test
    void inspect_ShouldCheckExactIdAcrossAllResourceTypesAndDeleteByAssetId() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("product/id"), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            if (options.get("resource_type").equals("image")
                    || options.get("resource_type").equals("raw")) {
                throw new ApiException("404 Not Found");
            }
            if (options.get("resource_type").equals("video")) {
                ApiResponse response = mock(ApiResponse.class);
                doReturn("product/id").when(response).get("public_id");
                doReturn("immutable-asset").when(response).get("asset_id");
                doReturn("video").when(response).get("resource_type");
                doReturn("upload").when(response).get("type");
                return response;
            }
            throw new AssertionError("unexpected resource type");
        });
        ApiResponse deleteResponse = mock(ApiResponse.class);
        when(api.deleteResourcesByAssetIds(anyList(), anyMap())).thenReturn(deleteResponse);
        doReturn(Map.of("immutable-asset", "deleted")).when(deleteResponse).get("deleted");

        CloudinaryMediaCleanupProvider provider = new CloudinaryMediaCleanupProvider(cloudinary);
        var inspected = provider.inspectExactPublicId("product/id", CleanupScope.BACKEND_ISSUED);
        provider.deleteExactAssets(inspected.assets());

        assertThat(inspected.assets()).hasSize(1);
        assertThat(inspected.assets().get(0).resourceType()).isEqualTo("video");
        verify(api, times(3)).resource(eq("product/id"), anyMap());
        verify(api).deleteResourcesByAssetIds(anyList(), anyMap());
    }

    @Test
    void inspect_ShouldFailClosedWhenProviderResponseIsNotExact() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        ApiResponse response = mock(ApiResponse.class);
        when(api.resource(eq("reserved/id"), anyMap())).thenReturn(response);
        doReturn("reserved/id-sibling").when(response).get("public_id");
        doReturn("asset").when(response).get("asset_id");
        doReturn("image").when(response).get("resource_type");
        doReturn("upload").when(response).get("type");

        CloudinaryMediaCleanupProvider provider = new CloudinaryMediaCleanupProvider(cloudinary);
        assertThrows(
                MediaCleanupProviderException.class,
                () -> provider.inspectExactPublicId("reserved/id", CleanupScope.BACKEND_ISSUED));
    }

    @Test
    void inspectLegacyMedia_ShouldNeverInspectOrDeleteSameIdVideoOrRawAssets() throws Exception {
        Cloudinary cloudinary = mock(Cloudinary.class);
        Api api = mock(Api.class);
        when(cloudinary.api()).thenReturn(api);
        when(api.resource(eq("legacy/public-id"), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            assertThat(options.get("resource_type")).isEqualTo("image");
            throw new ApiException("404 Not Found");
        });

        CloudinaryMediaCleanupProvider provider = new CloudinaryMediaCleanupProvider(cloudinary);
        var inspected = provider.inspectExactPublicId("legacy/public-id", CleanupScope.LEGACY_IMAGE_ONLY);
        provider.deleteExactAssets(inspected.assets());

        assertThat(inspected.isAbsent()).isTrue();
        verify(api, times(1)).resource(eq("legacy/public-id"), anyMap());
        verify(api, never()).deleteResourcesByAssetIds(anyList(), anyMap());
    }
}
