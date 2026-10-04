package com.xdpsx.ecommerce.refund.api;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundResponse;
import com.xdpsx.ecommerce.refund.application.RefundService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRefundController {
    private final RefundService refundService;

    @GetMapping("/{refundId}")
    public RefundResponse get(@PathVariable Long refundId) {
        return refundService.get(refundId);
    }

    @PostMapping("/{refundId}/complete")
    public RefundResponse complete(
            @PathVariable Long refundId,
            @Valid @RequestBody(required = false) RefundCompleteRequest request,
            Authentication authentication) {
        return refundService.complete(refundId, request, authentication.getName());
    }

    @PostMapping("/{refundId}/fail")
    public RefundResponse fail(
            @PathVariable Long refundId, @Valid @RequestBody RefundFailRequest request, Authentication authentication) {
        return refundService.fail(refundId, request, authentication.getName());
    }

    @PostMapping("/{refundId}/retry")
    public RefundResponse retry(@PathVariable Long refundId) {
        return refundService.retry(refundId);
    }
}
