package com.xdpsx.ecommerce.refund.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundFailRequest(@NotBlank @Size(max = 500) String failureReason) {}
