package com.xdpsx.ecommerce.payment.api;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xdpsx.ecommerce.payment.domain.Payment;
import com.xdpsx.ecommerce.payment.infrastructure.vnpay.IpnHandler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final IpnHandler ipnHandler;

    @GetMapping("/vnpay_ipn")
    String processIpn(@RequestParam Map<String, String> params, Authentication authentication) {
        log.info("[VNPay Ipn] Params: {}", params);
        return ipnHandler.process(params, authentication.getName());
    }
}
