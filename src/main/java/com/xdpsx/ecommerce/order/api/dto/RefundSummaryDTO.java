package com.xdpsx.ecommerce.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class RefundSummaryDTO {
    private Long id;
    private String status;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}
