package com.xdpsx.ecommerce.validations;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.constants.FileConstants;

public class ImgValidator implements ConstraintValidator<ImgConstraint, Object> {
    private int minWidth;
    private int maxNumber;

    @Override
    public void initialize(ImgConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
        this.minWidth = constraintAnnotation.minWidth();
        this.maxNumber = constraintAnnotation.maxNumber();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof List<?> files) {
            // Check if the list is empty or contains only MultipartFile instances
            if (files.isEmpty() || files.stream().allMatch(file -> file instanceof MultipartFile)) {
                if (files.size() > maxNumber) {
                    buildNewMessage(context, "Cannot upload more than %s images".formatted(maxNumber));
                    return false;
                }
                for (int i = 0; i < files.size(); i++) {
                    MultipartFile curFile = (MultipartFile) files.get(i);
                    if (validateImageType(curFile)) {
                        buildNewMessage(
                                context, "ERROR in Image %s: Only PNG or JPG images are supported".formatted(i));
                        return false;
                    }

                    if (validateImageWidth(curFile)) {
                        buildNewMessage(
                                context,
                                "ERROR in Image %s: Image width must be at least ".formatted(i) + minWidth + " pixels");
                        return false;
                    }
                }
            } else {
                buildNewMessage(context, "Invalid file type in the list");
                return false;
            }
        } else if (value instanceof MultipartFile file) {
            if (validateImageType(file)) {
                buildNewMessage(context, "Only PNG or JPG images are supported");
                return false;
            }

            if (validateImageWidth(file)) {
                buildNewMessage(context, "Image width must be at least " + minWidth + " pixels");
                return false;
            }
        } else {
            return true; // If the value is neither a List nor a MultipartFile, consider it valid
        }
        return true;
    }

    private void buildNewMessage(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }

    private boolean validateImageWidth(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            int imageWidth = image.getWidth();
            return imageWidth < minWidth;
        } catch (IOException e) {
            return true;
        }
    }

    private boolean validateImageType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType == null || !FileConstants.IMG_TYPES.contains(contentType);
    }
}
