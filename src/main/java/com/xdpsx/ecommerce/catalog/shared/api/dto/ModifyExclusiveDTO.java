package com.xdpsx.ecommerce.catalog.shared.api.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

public record ModifyExclusiveDTO(@NotNull LocalDateTime lastRetrievedAt) {}
