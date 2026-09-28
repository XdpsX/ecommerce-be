package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.util.Map;

import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;

public interface IpnHandler {
    VNPayIpnResponse process(Map<String, String> params);
}
