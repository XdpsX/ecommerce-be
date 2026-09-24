package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class CloudinaryUploader {
    private final Cloudinary cloudinary;
    private final ObjectMapper objectMapper;

    public CloudinaryUploadResponse uploadFile(MultipartFile file, Map uploadOptions) {
        try {
            Map response = cloudinary.uploader().upload(file.getBytes(), uploadOptions);
            return objectMapper.convertValue(response, CloudinaryUploadResponse.class);
        } catch (IOException io) {
            throw new RuntimeException("Uploading image to Cloudinary failed!", io);
        }
    }

    /**
     * Deletes the given asset, retrying transient failures.
     *
     * <p>Provider results {@code ok} and {@code not found} both count as success: an asset that is
     * already absent is the desired end state, which lets a retried cleanup finish its bookkeeping.
     *
     * @return {@code true} when the asset is confirmed gone, {@code false} after all attempts failed
     */
    public boolean deleteFile(String publicId) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                Object destroyResult = (result == null) ? null : result.get("result");
                if ("ok".equals(destroyResult) || "not found".equals(destroyResult)) {
                    return true;
                }
                log.warn("Unexpected result when deleting publicId {}: {}", publicId, destroyResult);
            } catch (IOException io) {
                log.error(
                        "IOException when deleting publicId {}: attempt {}/{}", publicId, attempt + 1, maxRetries, io);
            }
            attempt++;

            if (attempt < maxRetries) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while waiting to retry deletion of publicId {}", publicId);
                    return false;
                }
            }
        }

        log.error("Failed to delete image in Cloudinary after {} attempts, publicId {}", maxRetries, publicId);
        return false;
    }

    public String getFileUrl(String publicId) {
        return cloudinary
                .url()
                .publicId(publicId)
                .secure(true) // Sá»­ dá»¥ng HTTPS
                .generate();
    }
}
