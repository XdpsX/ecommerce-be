package com.xdpsx.ecommerce.payment.api;

import java.util.Map;

import com.xdpsx.ecommerce.payment.api.dto.VNPayIpnResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment Integration API")
interface PaymentApi {
    @Operation(summary = "Handle VNPay IPN", responses = @ApiResponse(responseCode = "200", description = "OK"))
    VNPayIpnResponse processIpn(Map<String, String> params);
}
