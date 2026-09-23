package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CryptoService {

    private final Mac mac = Mac.getInstance("HmacSHA512");

    @Value("${payment.vnpay.secret-key}")
    private String secretKey;

    public CryptoService() throws NoSuchAlgorithmException {}

    @PostConstruct
    void init() throws InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA512");
        mac.init(secretKeySpec);
    }

    public String sign(String data) {
        try {
            return EncodingUtil.toHexString(mac.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("VNPAY_SIGNING_FAILED");
        }
    }
}
