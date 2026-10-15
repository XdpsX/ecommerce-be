package com.xdpsx.ecommerce.media.application;

import com.xdpsx.ecommerce.media.domain.MediaPurpose;

public record CreateUploadIntentCommand(
        MediaPurpose purpose, String filename, String expectedContentType, long expectedSizeBytes) {}
