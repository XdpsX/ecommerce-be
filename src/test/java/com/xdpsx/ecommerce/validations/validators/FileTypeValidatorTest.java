package com.xdpsx.ecommerce.validations.validators;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.validations.annotations.FileTypeConstraint;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileTypeValidatorTest {

    @InjectMocks
    private FileTypeValidator validator;

    @BeforeEach
    void setUp() {
        FileTypeConstraint constraint = mock(FileTypeConstraint.class);
        when(constraint.allowedTypes()).thenReturn(new String[] {IMAGE_JPEG_VALUE, IMAGE_PNG_VALUE, IMAGE_GIF_VALUE});
        validator.initialize(constraint);
    }

    @DisplayName("1.1 should return false when file is null")
    @Order(1)
    @Test
    void isValid_ShouldReturnFalse_WhenFileIsNull() {
        assertFalse(validator.isValid(null, null));
    }

    @DisplayName("1.2 should return false when content type is null")
    @Order(2)
    @Test
    void isValid_ShouldReturnFalse_WhenContentTypeIsNull() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);

        assertFalse(validator.isValid(file, null));
    }

    @DisplayName("1.3 should return true when content type is valid and image is not null")
    @Order(3)
    @Test
    void isValid_ShouldReturnTrue_WhenContentTypeIsValidAndImageIsNotNull() throws Exception {
        MultipartFile file = createMockImageFile(IMAGE_PNG_VALUE, true);

        assertTrue(validator.isValid(file, null));
    }

    @DisplayName("1.4 should return false when content type is valid but image is null")
    @Order(4)
    @Test
    void isValid_ShouldReturnFalse_WhenContentTypeIsValidButImageIsNull() throws Exception {
        MultipartFile file = createMockImageFile(IMAGE_PNG_VALUE, false);

        assertFalse(validator.isValid(file, null));
    }

    @DisplayName("1.5 should return false when content type is valid but reading image fails")
    @Order(5)
    @Test
    void isValid_ShouldReturnFalse_WhenIOExceptionOccurs() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(IMAGE_PNG_VALUE);
        when(file.getInputStream()).thenThrow(new IOException("Simulated IOException"));

        assertFalse(validator.isValid(file, null));
    }

    @DisplayName("1.6 should return false when content type is invalid")
    @Order(6)
    @Test
    void isValid_ShouldReturnFalse_WhenContentTypeIsInvalid() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");

        assertFalse(validator.isValid(file, null));
    }

    private MultipartFile createMockImageFile(String contentType, boolean validImage) throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(contentType);

        if (validImage) {
            // Tạo 1 BufferedImage thật sự
            BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());

            when(file.getInputStream()).thenReturn(bais);
        } else {
            // Khi đọc inputstream, trả về rỗng để ImageIO.read = null
            ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
            when(file.getInputStream()).thenReturn(bais);
        }

        return file;
    }
}
