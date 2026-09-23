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
        // Kiểm tra xem Cloudinary bean có được khởi tạo không
        assertNotNull(cloudinary);

        // Thực hiện một yêu cầu đơn giản để kiểm tra kết nối
        Map<String, Object> response = cloudinary.api().resources(ObjectUtils.asMap("max_results", 1));

        // Kiểm tra xem phản hồi có hợp lệ không
        assertNotNull(response);
        System.out.println("Cloudinary connection successful: " + response);
    }
}
