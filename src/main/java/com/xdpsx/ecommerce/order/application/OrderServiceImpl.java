package com.xdpsx.ecommerce.order.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.inventory.domain.InventoryBalance;
import com.xdpsx.ecommerce.inventory.persistence.InventoryBalanceRepository;
import com.xdpsx.ecommerce.order.api.dto.*;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.order.persistence.OrderSpecification;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentAttemptRepository;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.user.domain.EmailIdentity;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryBalanceRepository inventoryBalanceRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Transactional
    @Override
    public boolean expirePendingOrder(long orderId, Instant cutoff) {
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", orderId)));
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                || order.getReservationExpiresAt() == null
                || !order.getReservationExpiresAt().isBefore(cutoff)) {
            return false;
        }

        List<Long> variantIds = order.getItems().stream()
                .map(item -> item.getVariantId())
                .distinct()
                .sorted()
                .toList();
        if (variantIds.isEmpty()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "orderHasNoItems"));
        }
        Map<Long, InventoryBalance> balances = new HashMap<>();
        inventoryBalanceRepository
                .findAllByVariantIdsForUpdate(variantIds)
                .forEach(balance -> balances.put(balance.getVariantId(), balance));
        if (balances.size() != Set.copyOf(variantIds).size()) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, Map.of("reason", "missingInventoryBalance"));
        }
        try {
            order.getItems().forEach(item -> balances.get(item.getVariantId()).release(item.getQuantity()));
        } catch (RuntimeException exception) {
            throw new ApplicationException(
                    ErrorCode.MALFORMED_REQUEST, Map.of("reason", "reservationUnavailable"), exception);
        }
        Payment payment = order.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            paymentAttemptRepository
                    .findPendingByPaymentIdForUpdate(payment.getId())
                    .forEach(attempt -> attempt.markExpired(cutoff));
            payment.markExpired();
            paymentRepository.save(payment);
        }
        order.markPaymentExpired();
        return true;
    }

    @Override
    public PageResponse<OrderDTO> getMyOrders(String userEmail, int pageNum, int pageSize) {
        User user = getUser(userEmail);
        Page<Order> orderPage = orderRepository.findByUser(user.getId(), PageRequest.of(pageNum - 1, pageSize));
        List<OrderDTO> responses = orderPage.getContent().stream()
                .map(order -> convertToDTO(order, false))
                .toList();
        return PageResponse.of(
                responses,
                orderPage.getNumber() + 1,
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Override
    public OrderDetailsDTO getOrderById(Long orderId) {
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", orderId)));
        return orderMapper.fromEntityToDetails(order);
    }

    @Override
    public PageResponse<OrderDTO> getAllOrders(
            int pageNum, int pageSize, OrderStatus orderStatus, PaymentStatus paymentStatus) {
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize);
        Specification<Order> spec = OrderSpecification.withStatusAndPaymentStatus(orderStatus, paymentStatus);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        List<OrderDTO> responses = orderPage.getContent().stream()
                .map(order -> convertToDTO(order, true))
                .toList();
        return PageResponse.of(
                responses,
                orderPage.getNumber() + 1,
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages());
    }

    @Transactional
    @Override
    public OrderDTO updateOrderStatus(Long id, OrderStatusUpdate request) {
        Order order = orderRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", id)));
        OrderStatus nextStatus = request.getStatus();
        try {
            order.advanceTo(nextStatus);
        } catch (IllegalStateException exception) {
            throw new ApplicationException(
                    ErrorCode.MALFORMED_REQUEST,
                    Map.of(
                            "reason", "invalidOrderTransition",
                            "from", order.getStatus().name(),
                            "to", String.valueOf(nextStatus)),
                    exception);
        }
        Order savedOrder = orderRepository.save(order);
        return convertToDTO(savedOrder, true);
    }

    @Override
    public OrderDetailsDTO getOrderByTrackingNumber(String name, String trackingNumber) {
        User user = getUser(name);
        Order order = orderRepository
                .findByUserIdAndTrackingNumber(user.getId(), trackingNumber)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("resourceType", "order", "trackingNumber", trackingNumber)));
        return orderMapper.fromEntityToCustomerDetails(order);
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user", "email", userEmail)));
    }

    private OrderDTO convertToDTO(Order savedOrder, boolean includeCancellationActor) {
        var shipping = savedOrder.getShippingAddress();
        return OrderDTO.builder()
                .id(savedOrder.getId())
                .trackingNumber(savedOrder.getTrackingNumber())
                .status(savedOrder.getStatus().name())
                .total(savedOrder.getTotalAmount())
                .address(shipping == null ? savedOrder.getAddress() : shipping.getAddressLine())
                .mobileNumber(shipping == null ? savedOrder.getMobileNumber() : shipping.getPhoneNumber())
                .recipientName(shipping == null ? null : shipping.getRecipientName())
                .phoneNumber(shipping == null ? savedOrder.getMobileNumber() : shipping.getPhoneNumber())
                .addressLine(shipping == null ? savedOrder.getAddress() : shipping.getAddressLine())
                .wardCommune(shipping == null ? null : shipping.getWardCommune())
                .district(shipping == null ? null : shipping.getDistrict())
                .provinceCity(shipping == null ? null : shipping.getProvinceCity())
                .postalCode(shipping == null ? null : shipping.getPostalCode())
                .currency(savedOrder.getCurrency())
                .paymentStatus(
                        savedOrder.getPayment() == null
                                ? null
                                : savedOrder.getPayment().getStatus().name())
                .createdAt(savedOrder.getCreatedAt())
                .deliveredAt(savedOrder.getDeliveredAt())
                .cancellationReason(savedOrder.getCancellationReason())
                .cancelledBy(includeCancellationActor ? savedOrder.getCancelledBy() : null)
                .cancelledAt(savedOrder.getCancelledAt())
                .refund(
                        savedOrder.getPayment() == null
                                        || savedOrder.getPayment().getRefund() == null
                                ? null
                                : toRefundSummary(savedOrder.getPayment().getRefund()))
                .build();
    }

    private static RefundSummaryDTO toRefundSummary(com.xdpsx.ecommerce.refund.domain.Refund refund) {
        RefundSummaryDTO dto = new RefundSummaryDTO();
        dto.setStatus(refund.getStatus().name());
        dto.setAmount(refund.getAmount());
        dto.setCurrency(refund.getCurrency());
        dto.setRequestedAt(refund.getRequestedAt());
        dto.setCompletedAt(refund.getCompletedAt());
        return dto;
    }

    //    private OrderDetailsDTO convertToOrderDetails(Order order) {
    //        if (order == null) {
    //            return null;
    //        }
    //
    //        List<OrderItemResponse> items = order.getItems().stream()
    //                .map(orderItem -> OrderItemResponse.builder()
    //                        .id(orderItem.getId())
    //                        .product(ProductResponse.builder()
    //                                .id(orderItem.getProduct().getId())
    //                                .name(orderItem.getProduct().getName())
    //                                .build())
    //                        .quantity(orderItem.getQuantity())
    //                        .build())
    //                .collect(Collectors.toList());
    //
    //        return OrderDetailsDTO.builder()
    //                .id(order.getId())
    //                .trackingNumber(order.getTrackingNumber())
    //                .status(order.getStatus().name()) // Chuyá»ƒn Ä‘á»•i enum thÃ nh String
    //                .total(order.getTotalAmount())
    //                .address(order.getAddress())
    //                .mobileNumber(order.getMobileNumber())
    //                .paymentStatus(order.getPayment() != null ? order.getPayment().getStatus().name() : null) //
    // Kiá»ƒm
    // tra null
    //                .createdAt(order.getCreatedAt())
    //                .deliveredAt(order.getDeliveredAt())
    //                .items(items)
    //                .build();
    //    }
}
