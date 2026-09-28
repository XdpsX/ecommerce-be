package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.common.error.ApplicationException;
import com.xdpsx.ecommerce.order.application.OrderService;
import com.xdpsx.ecommerce.order.application.PaymentCallbackResult;
import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayIpnHandler implements IpnHandler {
    private final VNPayService vnPayService;

    private final OrderService orderService;

    @Override
    public VNPayIpnResponse process(Map<String, String> params) {
        if (!vnPayService.verifyIpn(params)) {
            return response("97", "Invalid signature");
        }

        Long orderId = parseOrderId(params.get(VNPayParams.TXN_REF));
        if (orderId == null) return response("01", "Order not found");
        BigDecimal amount = parseAmount(params.get(VNPayParams.AMOUNT));
        if (amount == null) return response("04", "invalid amount");
        String responseCode = params.get(VNPayParams.RESPONSE_CODE);
        String transactionStatus = params.get(VNPayParams.TRANSACTION_STATUS);
        if (responseCode == null || transactionStatus == null) {
            return response("99", "Invalid request");
        }
        boolean successful = "00".equals(responseCode) && "00".equals(transactionStatus);
        try {
            PaymentCallbackResult result = orderService.processPaymentCallback(orderId, amount, successful);
            return result == PaymentCallbackResult.ALREADY_CONFIRMED
                    ? response("02", "Order already confirmed")
                    : response("00", "Confirm Success");
        } catch (ApplicationException exception) {
            return switch (exception.getCode()) {
                case RESOURCE_NOT_FOUND -> response("01", "Order not found");
                case MALFORMED_REQUEST -> response("04", "invalid amount");
                default -> response("99", "Unknow error");
            };
        } catch (RuntimeException exception) {
            log.error("VNPay IPN processing failed", exception);
            return response("99", "Unknow error");
        }
    }

    private static Long parseOrderId(String txnRef) {
        try {
            long orderId = Long.parseLong(txnRef);
            if (orderId <= 0) return null;
            return orderId;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static BigDecimal parseAmount(String rawAmount) {
        try {
            if (rawAmount == null
                    || rawAmount.isEmpty()
                    || rawAmount.chars().anyMatch(character -> character < '0' || character > '9')) {
                throw new NumberFormatException();
            }
            BigDecimal amount = new BigDecimal(rawAmount).movePointLeft(2);
            return amount;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static VNPayIpnResponse response(String code, String message) {
        return new VNPayIpnResponse(code, message);
    }
}
