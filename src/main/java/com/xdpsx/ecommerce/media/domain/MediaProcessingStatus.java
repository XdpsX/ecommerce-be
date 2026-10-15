package com.xdpsx.ecommerce.media.domain;

/** Lifecycle of the provider-owned eager transformations for a media asset. */
public enum MediaProcessingStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
