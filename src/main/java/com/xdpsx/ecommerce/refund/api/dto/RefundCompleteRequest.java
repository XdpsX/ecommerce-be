package com.xdpsx.ecommerce.refund.api.dto;

import jakarta.validation.constraints.Size;

public record RefundCompleteRequest(@Size(max = 100) String externalReference) {}
