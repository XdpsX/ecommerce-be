package com.xdpsx.ecommerce.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;

@ExtendWith(MockitoExtension.class)
class PaymentAttemptServiceTest {
    @Mock
    private PaymentAttemptPreparationService preparationService;

    @Mock
    private PaymentService paymentService;

    @Test
    void initialize_ShouldGenerateProviderUrlFromPersistedAttemptSnapshot() {
        Instant expiresAt = Instant.parse("2026-01-01T00:10:00Z");
        PreparedPaymentAttempt prepared =
                new PreparedPaymentAttempt(42L, 7L, "opaque-attempt", new BigDecimal("125.50"), "VND", expiresAt);
        when(preparationService.prepare("buyer@example.test", 42L)).thenReturn(prepared);
        when(paymentService.init(any()))
                .thenReturn(
                        InitPaymentResponse.builder().vnpUrl("https://provider").build());

        InitPaymentResponse response = new PaymentAttemptService(preparationService, paymentService)
                .initialize("buyer@example.test", 42L, "127.0.0.1");

        ArgumentCaptor<InitPaymentRequest> request = ArgumentCaptor.forClass(InitPaymentRequest.class);
        verify(paymentService).init(request.capture());
        assertThat(request.getValue().getTxnRef()).isEqualTo("opaque-attempt");
        assertThat(request.getValue().getAmount()).isEqualByComparingTo("125.50");
        assertThat(request.getValue().getCurrency()).isEqualTo("VND");
        assertThat(request.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(response.getAttemptReference()).isEqualTo("opaque-attempt");
        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void initialize_ShouldReuseCommittedAttemptWhenProviderUrlGenerationFails() {
        Instant expiresAt = Instant.parse("2026-01-01T00:10:00Z");
        PreparedPaymentAttempt prepared =
                new PreparedPaymentAttempt(42L, 7L, "opaque-attempt", new BigDecimal("125.50"), "VND", expiresAt);
        when(preparationService.prepare("buyer@example.test", 42L)).thenReturn(prepared);
        when(paymentService.init(any()))
                .thenThrow(new IllegalStateException("provider unavailable"))
                .thenReturn(
                        InitPaymentResponse.builder().vnpUrl("https://provider").build());

        PaymentAttemptService service = new PaymentAttemptService(preparationService, paymentService);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.initialize("buyer@example.test", 42L, "127.0.0.1"))
                .isInstanceOf(com.xdpsx.ecommerce.common.error.ApplicationException.class);
        InitPaymentResponse retry = service.initialize("buyer@example.test", 42L, "127.0.0.1");

        assertThat(retry.getAttemptReference()).isEqualTo("opaque-attempt");
        verify(preparationService, times(2)).prepare("buyer@example.test", 42L);
    }
}
