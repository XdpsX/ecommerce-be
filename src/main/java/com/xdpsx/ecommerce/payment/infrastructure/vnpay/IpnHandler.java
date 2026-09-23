package com.xdpsx.ecommerce.payment.infrastructure.vnpay;

import java.util.Map;

public interface IpnHandler {
    String process(Map<String, String> params, String userEmail);
}
