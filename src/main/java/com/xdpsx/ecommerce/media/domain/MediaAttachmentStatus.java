package com.xdpsx.ecommerce.media.domain;

/** Lifecycle of the attachment and cleanup relationship for a media asset. */
public enum MediaAttachmentStatus {
    TEMPORARY,
    ACTIVE,
    PENDING_DELETE
}
