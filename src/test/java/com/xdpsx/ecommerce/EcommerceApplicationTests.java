package com.xdpsx.ecommerce;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

@SpringBootTest
class EcommerceApplicationTests {
    @Autowired
    private Cloudinary cloudinary;

    @DisplayName("Test Cloudinary Connection")
    //    @Test
    public void testCloudinaryConnection() throws Exception {
        // Kiá»ƒm tra xem Cloudinary bean cÃ³ Ä‘Æ°á»£c khá»Ÿi táº¡o khÃ´ng
        assertNotNull(cloudinary);

        // Thá»±c hiá»‡n má»™t yÃªu cáº§u Ä‘Æ¡n giáº£n Ä‘á»ƒ kiá»ƒm tra káº¿t ná»‘i
        Map<String, Object> response = cloudinary.api().resources(ObjectUtils.asMap("max_results", 1));

        // Kiá»ƒm tra xem pháº£n há»“i cÃ³ há»£p lá»‡ khÃ´ng
        assertNotNull(response);
        System.out.println("Cloudinary connection successful: " + response);
    }
}
