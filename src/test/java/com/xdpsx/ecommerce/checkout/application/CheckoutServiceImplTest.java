package com.xdpsx.ecommerce.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.checkout.api.dto.CheckoutRequest;
import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.order.domain.Order;
import com.xdpsx.ecommerce.order.domain.OrderStatus;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentService;
import com.xdpsx.ecommerce.user.domain.User;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {
    @Mock
    private CheckoutTransactionService transactionService;

    @Mock
    private PaymentService paymentService;

    @Test
    void checkout_ShouldTranslatePaymentFailureAfterTransactionCommits() {
        Order order = pendingOrder();
        when(transactionService.execute("buyer@example.test", request(), "checkout-1"))
                .thenReturn(new CheckoutTransactionResult(order, false));
        when(paymentService.init(any())).thenThrow(new IllegalStateException("provider unavailable"));

        CheckoutService service = new CheckoutServiceImpl(transactionService, paymentService);

        assertThatThrownBy(() -> service.checkout("buyer@example.test", request(), "checkout-1", "127.0.0.1"))
                .isInstanceOfSatisfying(ApplicationException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorCode.PAYMENT_INITIALIZATION_FAILED));
        verify(transactionService).execute("buyer@example.test", request(), "checkout-1");
    }

    @Test
    void checkout_ShouldRetryPaymentInitializationForReplayedOrder() {
        Order order = pendingOrder();
        when(transactionService.execute("buyer@example.test", request(), "checkout-1"))
                .thenReturn(new CheckoutTransactionResult(order, false))
                .thenReturn(new CheckoutTransactionResult(order, true));
        when(paymentService.init(any()))
                .thenReturn(InitPaymentResponse.builder().vnpUrl("payment-url").build());

        CheckoutService service = new CheckoutServiceImpl(transactionService, paymentService);

        service.checkout("buyer@example.test", request(), "checkout-1", "127.0.0.1");
        var replay = service.checkout("buyer@example.test", request(), "checkout-1", "127.0.0.1");

        assertThat(replay.isReplayed()).isTrue();
        assertThat(replay.getPayment().getVnpUrl()).isEqualTo("payment-url");
        verify(paymentService, org.mockito.Mockito.times(2)).init(any());
    }

    private static CheckoutRequest request() {
        CheckoutRequest request = new CheckoutRequest();
        request.setAddressId(10L);
        return request;
    }

    private static Order pendingOrder() {
        return Order.builder()
                .id(42L)
                .user(User.builder().id(7L).email("buyer@example.test").build())
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(new BigDecimal("25.00"))
                .currency("VND")
                .build();
    }
}
