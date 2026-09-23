package com.xdpsx.ecommerce.catalog.product.api.dto;

import static com.xdpsx.ecommerce.common.validation.FileConstants.NUMBER_PRODUCT_IMAGES;
import static com.xdpsx.ecommerce.common.validation.FileConstants.PRODUCT_IMG_WIDTH;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.*;

import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.catalog.product.api.validation.ImgConstraint;

import lombok.Data;

@Data
public class ProductUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String slug;

    @Min(value = 0)
    @Max(value = 1_000_000_000)
    private BigDecimal price;

    private double discountPercent;
    private boolean inStock;
    private boolean isPublished;

    @Size(max = 4096)
    private String description;

    private Integer categoryId;
    private Integer brandId;

    @ImgConstraint(minWidth = PRODUCT_IMG_WIDTH, maxNumber = NUMBER_PRODUCT_IMAGES)
    private List<MultipartFile> images;

    private List<Long> removedImageIds;
}
