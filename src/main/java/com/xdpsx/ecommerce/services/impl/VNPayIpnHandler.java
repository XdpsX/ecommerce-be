package com.xdpsx.ecommerce.services.impl;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.xdpsx.ecommerce.constants.VNPayParams;
import com.xdpsx.ecommerce.exceptions.BadRequestException;
import com.xdpsx.ecommerce.services.IpnHandler;
import com.xdpsx.ecommerce.services.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class VNPayIpnHandler implements IpnHandler {
    private final VNPayService vnPayService;

    private final OrderService orderService;

    @Override
    public String process(Map<String, String> params, String userEmail) {
        if (!vnPayService.verifyIpn(params)) {
            throw new BadRequestException("Ipn is not valid");
        }

        var txnRef = params.get(VNPayParams.TXN_REF);
        var orderId = Long.parseLong(txnRef);
        orderService.payment(userEmail, orderId);
        return "Success";
    }
}
