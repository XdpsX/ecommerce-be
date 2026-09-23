package com.xdpsx.ecommerce.dtos.order;

import java.util.List;

import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class OrderDetailsDTO extends OrderDTO {
    private String username;
    private List<OrderItemResponse> items;
}
