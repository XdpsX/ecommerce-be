package com.xdpsx.ecommerce.order.application;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.api.dto.OrderItemResponse;
import com.xdpsx.ecommerce.order.domain.Order;

@Mapper(componentModel = "spring")
public abstract class OrderMapper {
    @Mapping(target = "status", source = "entity.status")
    @Mapping(target = "paymentStatus", source = "entity.payment.status")
    @Mapping(target = "total", source = "entity.totalAmount")
    @Mapping(target = "username", source = "entity.user.name")
    @Mapping(target = "items", ignore = true)
    abstract OrderDetailsDTO toDetails(Order entity);

    public OrderDetailsDTO fromEntityToDetails(Order entity) {
        OrderDetailsDTO dto = toDetails(entity);
        List<OrderItemResponse> items = entity.getItems().stream()
                .map(orderItem -> {
                    return OrderItemResponse.builder()
                            .id(orderItem.getId())
                            .productId(orderItem.getProductId())
                            .productName(orderItem.getProductName())
                            .variantId(orderItem.getVariantId())
                            .sku(orderItem.getSku())
                            .variantDescription(orderItem.getVariantDescription())
                            .unitBasePrice(orderItem.getUnitBasePrice())
                            .discountAmount(orderItem.getDiscountAmount())
                            .finalUnitPrice(orderItem.getFinalUnitPrice())
                            .quantity(orderItem.getQuantity())
                            .subtotal(orderItem.getSubtotal())
                            .currency(orderItem.getCurrency())
                            .build();
                })
                .toList();
        dto.setItems(items);
        if (entity.getShippingAddress() != null) {
            var shipping = entity.getShippingAddress();
            dto.setAddress(shipping.getAddressLine());
            dto.setMobileNumber(shipping.getPhoneNumber());
            dto.setRecipientName(shipping.getRecipientName());
            dto.setPhoneNumber(shipping.getPhoneNumber());
            dto.setAddressLine(shipping.getAddressLine());
            dto.setWardCommune(shipping.getWardCommune());
            dto.setDistrict(shipping.getDistrict());
            dto.setProvinceCity(shipping.getProvinceCity());
            dto.setPostalCode(shipping.getPostalCode());
        }
        dto.setCurrency(entity.getCurrency());
        return dto;
    }
}
