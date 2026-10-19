package com.xdpsx.ecommerce.media.application.cleanup;

import java.util.List;

/** Exact-identity provider operations used only after a Media cleanup claim is committed. */
public interface MediaCleanupProvider {
    Inspection inspectExactPublicId(String publicId, CleanupScope scope);

    void deleteExactAssets(List<AssetIdentity> assets);

    record AssetIdentity(String publicId, String assetId, String resourceType, String deliveryType) {}

    enum CleanupScope {
        LEGACY_IMAGE_ONLY,
        BACKEND_ISSUED
    }

    record Inspection(List<AssetIdentity> assets) {
        public Inspection {
            assets = List.copyOf(assets);
        }

        public boolean isAbsent() {
            return assets.isEmpty();
        }
    }
}
