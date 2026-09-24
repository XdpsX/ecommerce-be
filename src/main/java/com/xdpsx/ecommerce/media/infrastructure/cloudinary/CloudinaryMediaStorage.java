package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cloudinary.Transformation;
import com.xdpsx.ecommerce.media.application.storage.MediaStorage;
import com.xdpsx.ecommerce.media.application.storage.MediaStorageException;
import com.xdpsx.ecommerce.media.application.storage.MediaUploadCommand;
import com.xdpsx.ecommerce.media.application.storage.StoredMedia;
import com.xdpsx.ecommerce.media.domain.MediaPurpose;

import lombok.RequiredArgsConstructor;

/**
 * Cloudinary implementation of the {@link MediaStorage} port.
 *
 * <p>Provider details (folder layout, upload transformation, response shape and error semantics) are
 * confined here. The application layer only sees {@link StoredMedia} and
 * {@link MediaStorageException}.
 */
@Component
@RequiredArgsConstructor
public class CloudinaryMediaStorage implements MediaStorage {

    private static final Map<MediaPurpose, String> FOLDERS = Map.of(
            MediaPurpose.CATEGORY_IMAGE, "categories",
            MediaPurpose.BRAND_LOGO, "brands",
            MediaPurpose.PRODUCT_IMAGE, "products");

    private final CloudinaryUploader cloudinaryUploader;

    @Override
    public StoredMedia upload(MediaUploadCommand command) {
        MediaPurpose purpose = command.purpose();

        CloudinaryUploadResponse response;
        try {
            response = cloudinaryUploader.uploadFile(command.file(), uploadOptions(purpose));
        } catch (RuntimeException e) {
            throw new MediaStorageException("Media upload failed at the storage provider", e);
        }

        // A response without an identity is unusable: the asset cannot be tracked or deleted later.
        if (response == null || isBlank(response.publicId())) {
            throw new MediaStorageException("Storage provider returned an unusable upload response");
        }

        String externalId = response.publicId();
        // Prefer the secure URL; only fall back when the provider (or a legacy test double) omits it.
        String url = firstNonBlank(response.secureUrl(), response.url());

        if (isBlank(url)) {
            // The asset exists at the provider but cannot be referenced: remove it instead of leaking an orphan.
            removeOrphan(externalId);
            throw new MediaStorageException("Storage provider returned an upload response without a usable URL");
        }

        return new StoredMedia(externalId, url);
    }

    /**
     * Best-effort cleanup of an asset that must not be kept. Failure here only leaves a harmless orphan
     * and must not replace the caller's failure reason, so it is intentionally not rethrown.
     */
    private void removeOrphan(String externalId) {
        try {
            cloudinaryUploader.deleteFile(externalId);
        } catch (RuntimeException ignored) {
            // A leftover orphan is acceptable here; the reported upload failure is the meaningful outcome.
        }
    }

    @Override
    public void delete(String externalId) {
        boolean deleted;
        try {
            deleted = cloudinaryUploader.deleteFile(externalId);
        } catch (RuntimeException e) {
            throw new MediaStorageException("Media deletion failed at the storage provider", e);
        }

        if (!deleted) {
            throw new MediaStorageException("Storage provider did not confirm deletion of the media asset");
        }
    }

    private Map<String, Object> uploadOptions(MediaPurpose purpose) {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", FOLDERS.get(purpose));
        options.put(
                "transformation",
                new Transformation<>().width(purpose.minWidth()).crop("scale"));
        return options;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
