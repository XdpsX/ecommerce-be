package com.xdpsx.ecommerce.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.cart.domain.CartItem;
import com.xdpsx.ecommerce.cart.domain.CartItemId;
import com.xdpsx.ecommerce.cart.persistence.CartItemRepository;
import com.xdpsx.ecommerce.catalog.product.domain.Product;
import com.xdpsx.ecommerce.catalog.product.domain.ProductVariant;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.config.StorePricingProperties;
import com.xdpsx.ecommerce.order.api.dto.OrderDetailsDTO;
import com.xdpsx.ecommerce.order.api.dto.OrderRequest;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.persistence.OrderRepository;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentService;
import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.domain.PaymentMethod;
import com.xdpsx.ecommerce.payment.domain.PaymentStatus;
import com.xdpsx.ecommerce.payment.persistence.PaymentRepository;
import com.xdpsx.ecommerce.user.domain.User;
import com.xdpsx.ecommerce.user.persistence.UserRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StorePricingProperties storePricingProperties;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void placeOrder_ShouldRejectTheWholeCartWhenOneVariantIsNotAvailable() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Product product = Product.builder().id(11L).name("Shirt").slug("shirt").build();
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-BLACK")
                .build();
        CartItem item = CartItem.builder()
                .id(new CartItemId(user.getId(), variant.getId()))
                .user(user)
                .variant(variant)
                .quantity(1)
                .build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartItemRepository.findNewestByUserId(user.getId())).thenReturn(List.of(item));
        when(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()))
                .thenReturn(List.of());

        assertThrows(
                ApplicationException.class,
                () -> orderService.placeOrder(user.getEmail(), new com.xdpsx.ecommerce.order.api.dto.OrderRequest()));
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(paymentService, never()).init(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void placeOrder_ShouldPersistSkuMoneySnapshotAndTotalFromSubtotals() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Product product = Product.builder().id(11L).name("Shirt").slug("shirt").build();
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-BLACK")
                .basePrice(new BigDecimal("12.50"))
                .build();
        CartItem item = CartItem.builder()
                .id(new CartItemId(user.getId(), variant.getId()))
                .user(user)
                .variant(variant)
                .quantity(2)
                .build();
        com.xdpsx.ecommerce.order.api.dto.OrderRequest request = new com.xdpsx.ecommerce.order.api.dto.OrderRequest();
        request.setAddress("Address");
        request.setMobileNumber("0123456789");
        Order mappedOrder = Order.builder().build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartItemRepository.findNewestByUserId(user.getId())).thenReturn(List.of(item));
        when(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()))
                .thenReturn(List.of(101L));
        when(orderMapper.fromRequestToEntity(request)).thenReturn(mappedOrder);
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storePricingProperties.getCurrency()).thenReturn("VND");
        when(paymentService.init(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InitPaymentResponse.builder().vnpUrl("payment").build());

        orderService.placeOrder(user.getEmail(), request);

        assertEquals(new BigDecimal("25.00"), mappedOrder.getTotalAmount());
        assertEquals(1, mappedOrder.getItems().size());
        assertEquals(new BigDecimal("12.50"), mappedOrder.getItems().get(0).getUnitBasePrice());
        assertEquals(new BigDecimal("25.00"), mappedOrder.getItems().get(0).getSubtotal());
        assertEquals(101L, mappedOrder.getItems().get(0).getVariantId());
        assertEquals("VND", mappedOrder.getItems().get(0).getCurrency());
        verify(cartItemRepository, times(1)).findNewestByUserId(user.getId());
        verify(cartItemRepository, times(1)).findAvailableEligibleVariantIdsByUserId(user.getId());
    }

    @Test
    void orderDetails_ShouldKeepSnapshotWhenCatalogDataChanges() {
        User user = User.builder().id(7L).email("buyer@example.test").build();
        Product product = Product.builder().id(11L).name("Shirt").slug("shirt").build();
        ProductVariant variant = ProductVariant.builder()
                .id(101L)
                .product(product)
                .sku("SHIRT-BLACK")
                .basePrice(new BigDecimal("12.50"))
                .build();
        CartItem item = CartItem.builder()
                .id(new CartItemId(user.getId(), variant.getId()))
                .user(user)
                .variant(variant)
                .quantity(2)
                .build();
        OrderRequest request = new OrderRequest();
        request.setAddress("Address");
        request.setMobileNumber("0123456789");
        Order mappedOrder = Order.builder().build();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(cartItemRepository.findNewestByUserId(user.getId())).thenReturn(List.of(item));
        when(cartItemRepository.findAvailableEligibleVariantIdsByUserId(user.getId()))
                .thenReturn(List.of(101L));
        when(orderMapper.fromRequestToEntity(request)).thenReturn(mappedOrder);
        when(orderRepository.save(org.mockito.ArgumentMatchers.any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(storePricingProperties.getCurrency()).thenReturn("VND");
        when(paymentService.init(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InitPaymentResponse.builder().vnpUrl("payment").build());

        orderService.placeOrder(user.getEmail(), request);
        product.setName("Renamed shirt");
        variant.setSku("SHIRT-CHANGED");
        variant.setBasePrice(new BigDecimal("99.00"));

        OrderDetailsDTO details = Mappers.getMapper(OrderMapper.class).fromEntityToDetails(mappedOrder);

        assertEquals("Shirt", details.getItems().get(0).getProductName());
        assertEquals("SHIRT-BLACK", details.getItems().get(0).getSku());
        assertEquals(new BigDecimal("12.50"), details.getItems().get(0).getUnitBasePrice());
        assertEquals(new BigDecimal("25.00"), details.getItems().get(0).getSubtotal());
    }

    @Test
    void processPaymentCallback_ShouldMarkUnpaidOrderPaidAfterAmountAndStatusValidation() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, new BigDecimal("10.00"), true);

        assertEquals(PaymentStatus.PAID, order.getPayment().getStatus());
        assertEquals(PaymentMethod.VNPAY, order.getPayment().getPaymentMethod());
        verify(paymentRepository).save(order.getPayment());
    }

    @Test
    void processPaymentCallback_ShouldBeIdempotentForAlreadyPaidOrder() {
        Order order = order(BigDecimal.TEN, PaymentStatus.PAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        orderService.processPaymentCallback(42L, BigDecimal.TEN, true);

        verify(paymentRepository, never()).save(order.getPayment());
    }

    @Test
    void processPaymentCallback_ShouldRejectAmountMismatchWithoutChangingPayment() {
        Order order = order(BigDecimal.TEN, PaymentStatus.UNPAID);
        when(orderRepository.findByIdForUpdate(42L)).thenReturn(java.util.Optional.of(order));

        assertThrows(
                ApplicationException.class,
                () -> orderService.processPaymentCallback(42L, new BigDecimal("11.00"), true));

        assertEquals(PaymentStatus.UNPAID, order.getPayment().getStatus());
        verify(paymentRepository, never()).save(order.getPayment());
    }

    private static Order order(BigDecimal amount, PaymentStatus status) {
        Order order = Order.builder().id(42L).totalAmount(amount).build();
        order.setPayment(Payment.builder().order(order).status(status).build());
        return order;
    }
}
