package com.xdpsx.ecommerce.media.infrastructure.cloudinary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CloudinaryConfigTest {
    @Test
    void cloudinary_ShouldUseFiniteTenSecondSdkTimeout() {
        CloudinaryConfig config = new CloudinaryConfig();
        ReflectionTestUtils.setField(config, "apiKey", "public-key");
        ReflectionTestUtils.setField(config, "apiSecret", "secret");
        ReflectionTestUtils.setField(config, "cloudName", "demo");
        ReflectionTestUtils.setField(config, "timeoutSeconds", 10);

        assertEquals(10, config.cloudinary().config.timeout);
    }

    @Test
    void cloudinary_ShouldRejectUnboundedTimeoutConfiguration() {
        CloudinaryConfig config = new CloudinaryConfig();
        ReflectionTestUtils.setField(config, "timeoutSeconds", 0);

        assertThrows(IllegalStateException.class, config::cloudinary);
    }
}
