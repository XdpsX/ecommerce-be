package com.xdpsx.ecommerce.refund.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.xdpsx.ecommerce.common.pagination.PageConstants;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundQueueItemResponse;
import com.xdpsx.ecommerce.refund.api.dto.RefundResponse;
import com.xdpsx.ecommerce.refund.application.RefundService;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRefundController {
    private final RefundService refundService;

    @GetMapping
    public PageResponse<RefundQueueItemResponse> getQueue(
            @RequestParam(required = false) RefundStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "5") @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize) {
        return refundService.getQueue(status, pageNum, pageSize);
    }

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
