package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.common.error.ErrorCode;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;
import com.xdpsx.ecommerce.payment.application.PaymentAttemptCallbackService;

@ExtendWith(MockitoExtension.class)
class VNPayIpnHandlerTest {
    @Mock
    private VNPayService vnPayService;

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentAttemptCallbackService paymentAttemptCallbackService;

    @InjectMocks
    private VNPayIpnHandler handler;

    @Test
    void process_ShouldApplySignedSuccessfulCallbackWithoutHttpIdentity() {
        Map<String, String> params = callback("42", "10000", "00", "00");
        when(vnPayService.verifyIpn(params)).thenReturn(true);
        when(orderService.processPaymentCallback(eq(42L), org.mockito.ArgumentMatchers.any(), eq(true)))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        assertEquals(new VNPayIpnResponse("00", "Confirm Success"), handler.process(params));

        verify(orderService)
                .processPaymentCallback(
                        eq(42L), argThat(amount -> amount.compareTo(new BigDecimal("100.00")) == 0), eq(true));
    }

    @Test
    void process_ShouldKeepPaymentUnpaidForSignedFailedProviderResult() {
        Map<String, String> params = callback("42", "10000", "24", "02");
        when(vnPayService.verifyIpn(params)).thenReturn(true);
        when(orderService.processPaymentCallback(eq(42L), org.mockito.ArgumentMatchers.any(), eq(false)))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        assertEquals(new VNPayIpnResponse("00", "Confirm Success"), handler.process(params));

        verify(orderService)
                .processPaymentCallback(
                        eq(42L), argThat(amount -> amount.compareTo(new BigDecimal("100.00")) == 0), eq(false));
    }

    @Test
    void process_ShouldRejectUnsignedCallbackBeforeLoadingOrder() {
        Map<String, String> params = callback("42", "10000", "00", "00");
        when(vnPayService.verifyIpn(params)).thenReturn(false);

        assertEquals(new VNPayIpnResponse("97", "Invalid signature"), handler.process(params));

        verify(orderService, never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void process_ShouldRejectMalformedOrderReference() {
        Map<String, String> params = callback("not-an-order", "10000", "00", "00");
        when(vnPayService.verifyIpn(params)).thenReturn(true);
        when(paymentAttemptCallbackService.process(
                        eq("not-an-order"),
                        org.mockito.ArgumentMatchers.any(),
                        eq(true),
                        org.mockito.ArgumentMatchers.any(),
                        eq("00")))
                .thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        assertEquals(new VNPayIpnResponse("01", "Order not found"), handler.process(params));

        verify(orderService, never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void process_ShouldDelegateOpaqueReferenceToAttemptCallbackService() {
        Map<String, String> params = callback("attempt-uuid", "10000", "00", "00");
        params.put(VNPayParams.TRANSACTION_NO, "provider-transaction");
        when(vnPayService.verifyIpn(params)).thenReturn(true);
        when(paymentAttemptCallbackService.process(
                        eq("attempt-uuid"),
                        org.mockito.ArgumentMatchers.any(),
                        eq(true),
                        eq("provider-transaction"),
                        eq("00")))
                .thenReturn(PaymentCallbackResult.CONFIRMED);

        assertEquals(new VNPayIpnResponse("00", "Confirm Success"), handler.process(params));

        verify(paymentAttemptCallbackService)
                .process(
                        eq("attempt-uuid"),
                        argThat(amount -> amount.compareTo(new BigDecimal("100.00")) == 0),
                        eq(true),
                        eq("provider-transaction"),
                        eq("00"));
        verify(orderService, never())
                .processPaymentCallback(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void process_ShouldMapOrderValidationResultsToVnPayCodes() {
        Map<String, String> params = callback("42", "10000", "00", "00");
        when(vnPayService.verifyIpn(params)).thenReturn(true);
        when(orderService.processPaymentCallback(eq(42L), org.mockito.ArgumentMatchers.any(), eq(true)))
                .thenThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));

        assertEquals(new VNPayIpnResponse("01", "Order not found"), handler.process(params));

        when(orderService.processPaymentCallback(eq(42L), org.mockito.ArgumentMatchers.any(), eq(true)))
                .thenThrow(new ApplicationException(ErrorCode.MALFORMED_REQUEST));
        assertEquals(new VNPayIpnResponse("04", "invalid amount"), handler.process(params));

        when(orderService.processPaymentCallback(eq(42L), org.mockito.ArgumentMatchers.any(), eq(true)))
                .thenReturn(PaymentCallbackResult.ALREADY_CONFIRMED);
        assertEquals(new VNPayIpnResponse("02", "Order already confirmed"), handler.process(params));
    }

    private static Map<String, String> callback(String txnRef, String amount, String responseCode, String status) {
        Map<String, String> params = new HashMap<>();
        params.put(VNPayParams.TXN_REF, txnRef);
        params.put(VNPayParams.AMOUNT, amount);
        params.put(VNPayParams.RESPONSE_CODE, responseCode);
        params.put(VNPayParams.TRANSACTION_STATUS, status);
        return params;
    }
}
