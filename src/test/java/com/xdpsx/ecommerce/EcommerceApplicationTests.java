package com.xdpsx.ecommerce;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;

@SpringBootTest
class EcommerceApplicationTests {
    @Autowired
    private Cloudinary cloudinary;

    @DisplayName("Test Cloudinary Connection")
    //    @Test
    public void testCloudinaryConnection() throws Exception {
        assertNotNull(cloudinary);
        ApiResponse response = cloudinary.api().resources(ObjectUtils.asMap("max_results", 1));

        assertNotNull(response);
        System.out.println("Cloudinary connection successful: " + response);
    }
}
