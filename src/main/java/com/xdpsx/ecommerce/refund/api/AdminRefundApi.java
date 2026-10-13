package com.xdpsx.ecommerce.refund.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.core.Authentication;

import com.xdpsx.ecommerce.common.pagination.PageConstants;
import com.xdpsx.ecommerce.common.pagination.PageResponse;
import com.xdpsx.ecommerce.refund.api.dto.RefundCompleteRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundFailRequest;
import com.xdpsx.ecommerce.refund.api.dto.RefundQueueItemResponse;
import com.xdpsx.ecommerce.refund.api.dto.RefundResponse;
import com.xdpsx.ecommerce.refund.domain.RefundStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Refund API")
interface AdminRefundApi {
    @Operation(
            summary = "List refund queue",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    PageResponse<RefundQueueItemResponse> getQueue(
            RefundStatus status, @Min(1) int pageNum, @Min(1) @Max(PageConstants.MAX_ITEMS_PER_PAGE) int pageSize);

    @Operation(
            summary = "Get refund",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    RefundResponse get(Long refundId);

    @Operation(
            summary = "Complete refund",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    RefundResponse complete(Long refundId, @Valid RefundCompleteRequest request, Authentication authentication);

    @Operation(
            summary = "Fail refund",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    RefundResponse fail(Long refundId, @Valid RefundFailRequest request, Authentication authentication);

    @Operation(
            summary = "Retry refund",
            security = @SecurityRequirement(name = "Bearer Authorization"),
            responses = @ApiResponse(responseCode = "200", description = "OK"))
    RefundResponse retry(Long refundId);
}
