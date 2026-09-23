package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import java.io.IOException;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    public void deleteFile(String publicId) {
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries) {
            try {
                Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
                String destroyResult = (String) result.get("result");
                if (destroyResult.equals("ok") || destroyResult.equals("not found")) {
                    success = true;
                    break;
                } else {
                    log.warn("Unexpected result when deleting publicId {}: {}", publicId, destroyResult);
                }
            } catch (IOException io) {
                log.error(
                        "IOException when deleting publicId {}: attempt {}/{}", publicId, attempt + 1, maxRetries, io);
            }
            attempt++;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

        if (!success) {
            log.error("Failed to delete image in Cloudinary after {} attempts, publicId {}", maxRetries, publicId);
            savePendingDeletion(publicId);
        }
    }

    private void savePendingDeletion(String publicId) {
        // TODO: Implement a mechanism to save the publicId for later deletion retry
        log.info("Saving publicId {} for later deletion retry", publicId);
    }

    public String getFileUrl(String publicId) {
        return cloudinary
                .url()
                .publicId(publicId)
                .secure(true) // Sá»­ dá»¥ng HTTPS
                .generate();
    }
}
