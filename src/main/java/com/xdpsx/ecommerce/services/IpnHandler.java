package com.xdpsx.ecommerce.services;

import java.util.Map;

public interface IpnHandler {
    String process(Map<String, String> params, String userEmail);
}
