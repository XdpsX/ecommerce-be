package com.xdpsx.ecommerce.payment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VNPayIpnResponse(@JsonProperty("RspCode") String rspCode, @JsonProperty("Message") String message) {}
