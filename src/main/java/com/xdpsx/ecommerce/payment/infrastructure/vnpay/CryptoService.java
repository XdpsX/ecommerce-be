package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    @Value("${payment.vnpay.secret-key}")
    private String secretKey;

    private SecretKeySpec signingKey;

    @PostConstruct
    void init() {
        signingKey = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
    }

    public String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(signingKey);
            return EncodingUtil.toHexString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("VNPAY_SIGNING_FAILED");
        }
    }
}
