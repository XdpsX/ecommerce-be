package com.xdpsx.ecommerce.order.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.order.api.dto.*;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderItem;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.order.persistence.OrderSpecification;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentService;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentMethod;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
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
    private final CartItemRepository cartItemRepository;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final StorePricingProperties storePricingProperties;
    private final Clock pricingClock;

    @Transactional
    @Override
    public OrderResponse placeOrder(String userEmail, OrderRequest orderRequest) {
        User user = getUser(userEmail);
        Instant now = now();
        List<CartItem> cartItems = cartItemRepository.findNewestByUserId(user.getId());
        if (cartItems.isEmpty()) {
            throw new ApplicationException(ErrorCode.CART_EMPTY);
        }
        Set<Long> availableVariantIds =
                Set.copyOf(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()));
        if (cartItems.stream()
                .map(item -> item.getVariant().getId())
                .anyMatch(id -> !availableVariantIds.contains(id))) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }
        Order order = orderMapper.fromRequestToEntity(orderRequest);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            var variant = item.getVariant();
            var resolved = variant.resolvePriceAt(now);
            BigDecimal unitPrice = resolved.finalUnitPrice();
            BigDecimal subtotal =
                    unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2);
            OrderItem orderItem = OrderItem.builder()
                    .quantity(item.getQuantity())
                    .productId(variant.getProduct().getId())
                    .productName(variant.getProduct().getName())
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .variantDescription(variant.getSelections().stream()
                            .sorted(java.util.Comparator.comparing(
                                            (com.xdpsx.ecommerce.catalog.product.domain.ProductVariantSelection s) ->
                                                    s.getOptionValue()
                                                            .getOption()
                                                            .getDisplayOrder())
                                    .thenComparing(
                                            s -> s.getOptionValue().getOption().getId())
                                    .thenComparing(s -> s.getOptionValue().getDisplayOrder())
                                    .thenComparing(s -> s.getOptionValue().getId()))
                            .map(s -> s.getOptionValue().getOption().getName() + ": "
                                    + s.getOptionValue().getName())
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .unitBasePrice(resolved.basePrice())
                    .discountAmount(resolved.discountAmount())
                    .finalUnitPrice(unitPrice)
                    .subtotal(subtotal)
                    .currency(storePricingProperties == null ? "VND" : storePricingProperties.getCurrency())
                    .order(order)
                    .build();
            order.getItems().add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }
        order.setTrackingNumber(UUID.randomUUID().toString());
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(totalAmount);

        Payment payment =
                Payment.builder().status(PaymentStatus.UNPAID).order(order).build();
        order.setPayment(payment);

        Order savedOrder = orderRepository.save(order);

        //        cartItemRepository.deleteInStockCartByUserId(user.getId());

        var initPaymentRequest = InitPaymentRequest.builder()
                .userId(savedOrder.getUser().getId())
                .amount(savedOrder.getTotalAmount())
                .txnRef(String.valueOf(savedOrder.getId()))
                .requestId(String.valueOf(savedOrder.getId()))
                .ipAddress(orderRequest.getIpAddress())
                .build();
        InitPaymentResponse initPaymentResponse = paymentService.init(initPaymentRequest);

        OrderDTO orderDTO = convertToDTO(savedOrder);
        return OrderResponse.builder()
                .order(orderDTO)
                .payment(initPaymentResponse)
                .build();
    }

    @Override
    public void payment(String userEmail, long orderId) {
        User user = getUser(userEmail);
        Order order = orderRepository
                .findById(orderId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", orderId)));
        if (!user.getId().equals(order.getUser().getId())) {
            throw new ApplicationException(
                    ErrorCode.ACCESS_DENIED, Map.of("resourceType", "order", "resourceId", orderId));
        }
        Payment payment = order.getPayment();
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentDate(LocalDateTime.now());

        paymentRepository.save(payment);
    }

    @Transactional
    @Override
    public PaymentCallbackResult processPaymentCallback(long orderId, BigDecimal amount, boolean successful) {
        Order order = orderRepository
                .findByIdForUpdate(orderId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", orderId)));
        if (order.getTotalAmount() == null
                || amount == null
                || order.getTotalAmount().compareTo(amount) != 0) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }

        Payment payment = order.getPayment();
        if (payment == null) throw new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        if (payment.getStatus() == PaymentStatus.PAID) return PaymentCallbackResult.ALREADY_CONFIRMED;
        if (!successful) return PaymentCallbackResult.CONFIRMED;

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaymentMethod(PaymentMethod.VNPAY);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
        return PaymentCallbackResult.CONFIRMED;
    }

    @Override
    public PageResponse<OrderDTO> getMyOrders(String userEmail, int pageNum, int pageSize) {
        User user = getUser(userEmail);
        Page<Order> orderPage = orderRepository.findByUser(user.getId(), PageRequest.of(pageNum - 1, pageSize));
        List<OrderDTO> responses =
                orderPage.getContent().stream().map(this::convertToDTO).toList();
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
        List<OrderDTO> responses =
                orderPage.getContent().stream().map(this::convertToDTO).toList();
        return PageResponse.of(
                responses,
                orderPage.getNumber() + 1,
                orderPage.getSize(),
                orderPage.getTotalElements(),
                orderPage.getTotalPages());
    }

    @Override
    public OrderDTO updateOrderStatus(Long id, OrderStatusUpdate request) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "order", "resourceId", id)));
        order.setStatus(request.getStatus());
        if (request.getStatus().equals(OrderStatus.DELIVERED)) {
            order.setDeliveredAt(LocalDateTime.now());
        }
        Order savedOrder = orderRepository.save(order);
        return convertToDTO(savedOrder);
    }

    @Override
    public OrderDetailsDTO getOrderByTrackingNumber(String name, String trackingNumber) {
        User user = getUser(name);
        Order order = orderRepository
                .findByUserIdAndTrackingNumber(user.getId(), trackingNumber)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("resourceType", "order", "trackingNumber", trackingNumber)));
        return orderMapper.fromEntityToDetails(order);
    }

    private User getUser(String userEmail) {
        return userRepository
                .findByEmail(EmailIdentity.canonicalize(userEmail))
                .orElseThrow(() -> new ApplicationException(
                        ErrorCode.RESOURCE_NOT_FOUND, Map.of("resourceType", "user", "email", userEmail)));
    }

    private Instant now() {
        return (pricingClock == null ? Clock.systemUTC() : pricingClock).instant();
    }

    private OrderDTO convertToDTO(Order savedOrder) {
        return OrderDTO.builder()
                .id(savedOrder.getId())
                .trackingNumber(savedOrder.getTrackingNumber())
                .status(savedOrder.getStatus().name())
                .total(savedOrder.getTotalAmount())
                .mobileNumber(savedOrder.getMobileNumber())
                .paymentStatus(savedOrder.getPayment().getStatus().name())
                .address(savedOrder.getAddress())
                .createdAt(savedOrder.getCreatedAt())
                .deliveredAt(savedOrder.getDeliveredAt())
                .build();
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
