package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;

class VNPayServiceTest {
    private VNPayService service;

    @BeforeEach
    void setUp() {
        CryptoService cryptoService = Mockito.mock(CryptoService.class);
        when(cryptoService.sign(Mockito.anyString())).thenReturn("signature");
        service = new VNPayService(cryptoService, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "tmnCode", "TMNCODE");
        ReflectionTestUtils.setField(service, "initPaymentPrefixUrl", "https://provider/pay");
        ReflectionTestUtils.setField(service, "returnUrlFormat", "https://shop/payments/%s");
        ReflectionTestUtils.setField(service, "paymentTimeout", 15);
    }

    @Test
    void init_ShouldUsePersistedReferenceAmountCurrencyAndVietnamTime() {
        String url = service.init(InitPaymentRequest.builder()
                        .requestId("request")
                        .txnRef("attempt-1")
                        .amount(new BigDecimal("125.50"))
                        .currency("VND")
                        .expiresAt(Instant.parse("2026-01-01T00:10:00Z"))
                        .ipAddress("127.0.0.1")
                        .build())
                .getVnpUrl();

        assertThat(url).contains("vnp_Amount=12550");
        assertThat(url).contains("vnp_TxnRef=attempt-1");
        assertThat(url).contains("vnp_CreateDate=20260101070000");
        assertThat(url).contains("vnp_ExpireDate=20260101071000");
        assertThat(url).contains("vnp_CurrCode=VND");
    }

    @Test
    void init_ShouldRejectAmountsThatCannotBeRepresentedAsIntegerSubunits() {
        assertThatThrownBy(() -> service.init(InitPaymentRequest.builder()
                        .txnRef("attempt-1")
                        .amount(new BigDecimal("10.001"))
                        .currency("VND")
                        .build()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
