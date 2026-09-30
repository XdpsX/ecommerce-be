package com.xdpsx.ecommerce.catalog.product.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateProductVariantBarcodeRequest(@Size(max = 128) String barcode) {}
