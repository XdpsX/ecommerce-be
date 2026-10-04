package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.payment.api.dto.InitPaymentRequest;
import com.xdpsx.ecommerce.payment.api.dto.InitPaymentResponse;
import com.xdpsx.ecommerce.payment.application.PaymentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VNPayService implements PaymentService {
    public static final String VERSION = "2.1.0";
    public static final String COMMAND = "pay";
    public static final String ORDER_TYPE = "190000";
    public static final long DEFAULT_MULTIPLIER = 100L;

    @Value("${payment.vnpay.tmn-code}")
    private String tmnCode;

    @Value("${payment.vnpay.init-payment-url}")
    private String initPaymentPrefixUrl;

    @Value("${payment.vnpay.return-url}")
    private String returnUrlFormat;

    @Value("${payment.vnpay.timeout}")
    private Integer paymentTimeout;

    private final CryptoService cryptoService;
    private final Clock clock;

    @Override
    public InitPaymentResponse init(InitPaymentRequest request) {
        var amount = toVnPayAmount(request.getAmount());
        var txnRef = requireReference(request.getTxnRef());
        var returnUrl = buildReturnUrl(txnRef);
        Instant createdAt = clock.instant();
        Instant expiresAt = request.getExpiresAt();
        if (expiresAt == null) {
            int timeoutMinutes = paymentTimeout == null ? 15 : paymentTimeout;
            expiresAt = createdAt.plusSeconds(timeoutMinutes * 60L);
        }
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("Payment expiry must be in the future");

        var ipAddress = request.getIpAddress();
        var orderInfo = buildPaymentDetail(request);
        Map<String, String> params = new HashMap<>();

        params.put(VNPayParams.VERSION, VERSION);
        params.put(VNPayParams.COMMAND, COMMAND);

        params.put(VNPayParams.TMN_CODE, tmnCode);
        params.put(VNPayParams.AMOUNT, amount);
        params.put(VNPayParams.CURRENCY, requireCurrency(request.getCurrency()));

        params.put(VNPayParams.TXN_REF, txnRef);
        params.put(VNPayParams.RETURN_URL, returnUrl);

        params.put(VNPayParams.CREATED_DATE, DateUtil.formatVnTime(createdAt));
        params.put(VNPayParams.EXPIRE_DATE, DateUtil.formatVnTime(expiresAt));

        params.put(VNPayParams.IP_ADDRESS, ipAddress);
        params.put(VNPayParams.LOCALE, "vn");

        params.put(VNPayParams.ORDER_INFO, orderInfo);
        params.put(VNPayParams.ORDER_TYPE, ORDER_TYPE);

        var initPaymentUrl = buildInitPaymentUrl(params);
        return InitPaymentResponse.builder().vnpUrl(initPaymentUrl).build();
    }

    private static String toVnPayAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount is invalid");
        try {
            return amount.movePointRight(2).toBigIntegerExact().toString();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Payment amount cannot be represented by VNPay", exception);
        }
    }

    private static String requireReference(String reference) {
        if (reference == null || reference.isBlank() || reference.length() > 100) {
            throw new IllegalArgumentException("Payment reference is invalid");
        }
        return reference;
    }

    private static String requireCurrency(String currency) {
        String resolvedCurrency = currency == null ? "VND" : currency;
        if (!"VND".equals(resolvedCurrency)) throw new IllegalArgumentException("Only VND payments are supported");
        return resolvedCurrency;
    }

    public boolean verifyIpn(Map<String, String> params) {
        Map<String, String> signedParams = new HashMap<>(params);
        var reqSecureHash = signedParams.remove(VNPayParams.SECURE_HASH);
        signedParams.remove(VNPayParams.SECURE_HASH_TYPE);
        if (reqSecureHash == null || !tmnCode.equals(signedParams.get(VNPayParams.TMN_CODE))) return false;

        var hashPayload = new StringBuilder();
        var fieldNames = new ArrayList<>(signedParams.keySet());
        Collections.sort(fieldNames);

        var itr = fieldNames.iterator();
        while (itr.hasNext()) {
            var fieldName = itr.next();
            var fieldValue = signedParams.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                // Build hash data
                hashPayload.append(fieldName);
                hashPayload.append(Symbol.EQUAL);
                hashPayload.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                if (itr.hasNext()) {
                    hashPayload.append(Symbol.AND);
                }
            }
        }

        var secureHash = cryptoService.sign(hashPayload.toString());
        return MessageDigest.isEqual(
                secureHash.getBytes(StandardCharsets.US_ASCII), reqSecureHash.getBytes(StandardCharsets.US_ASCII));
    }

    private String buildPaymentDetail(InitPaymentRequest request) {
        return String.format("Payment for order %s", request.getTxnRef());
    }

    private String buildReturnUrl(String txnRef) {
        return String.format(returnUrlFormat, txnRef);
    }

    private String buildInitPaymentUrl(Map<String, String> params) {
        var hashPayload = new StringBuilder();
        var query = new StringBuilder();
        var fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames); // 1. Sort field names

        var itr = fieldNames.iterator();
        while (itr.hasNext()) {
            var fieldName = itr.next();
            var fieldValue = params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                // 2.1. Build hash data
                hashPayload.append(fieldName);
                hashPayload.append(Symbol.EQUAL);
                hashPayload.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                // 2.2. Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append(Symbol.EQUAL);
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                if (itr.hasNext()) {
                    query.append(Symbol.AND);
                    hashPayload.append(Symbol.AND);
                }
            }
        }

        // 3. Build secureHash
        var secureHash = cryptoService.sign(hashPayload.toString());

        // 4. Finalize query
        query.append("&vnp_SecureHash=");
        query.append(secureHash);

        return initPaymentPrefixUrl + "?" + query;
    }
}
