package com.xdpsx.ecommerce.order.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class OrderDTO {
    private Long id;
    private String trackingNumber;
    private String status;
    private BigDecimal total;
    private String address;
    private String mobileNumber;
    private String paymentStatus;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
}
