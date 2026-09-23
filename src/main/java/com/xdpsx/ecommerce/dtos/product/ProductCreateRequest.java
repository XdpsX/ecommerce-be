package com.xdpsx.ecommerce.dtos.product;

import static com.xdpsx.ecommerce.constants.FileConstants.*;

import java.util.List;

import jakarta.validation.constraints.*;

import org.springframework.web.multipart.MultipartFile;

import com.xdpsx.ecommerce.validations.ImgConstraint;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreateRequest {
    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String slug;

    @Min(value = 0)
    @Max(value = 1_000_000_000)
    private double price;

    @Min(value = 0)
    @Max(value = 100)
    private double discountPercent;

    private boolean inStock;

    private boolean published;

    @Size(max = 4096)
    private String description;

    @NotNull
    private Integer categoryId;

    @NotNull
    private Integer brandId;

    @ImgConstraint(minWidth = PRODUCT_IMG_WIDTH, maxNumber = NUMBER_PRODUCT_IMAGES)
    private List<MultipartFile> images;
}
