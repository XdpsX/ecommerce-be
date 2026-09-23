package com.xdpsx.ecommerce.order.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;

import com.xdpsx.ecommerce.order.domain.Order;

public class RequestUtil {
    public static String getIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null) {
            var remoteAddr = request.getRemoteAddr();
            if (remoteAddr == null) {
                remoteAddr = "127.0.0.1";
            }

            return remoteAddr;
        }

        return xForwardedForHeader.split(",")[0].trim();
    }
}
