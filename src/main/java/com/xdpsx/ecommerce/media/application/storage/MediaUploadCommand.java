package com.xdpsx.ecommerce.media.application.storage;

import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public record MediaUploadCommand(MultipartFile file, MediaPurpose purpose) {}
