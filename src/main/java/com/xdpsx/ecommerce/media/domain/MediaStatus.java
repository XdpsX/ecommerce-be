package com.xdpsx.ecommerce.media.domain;

/**
 * Lifecycle of a Media asset.
 *
 * <pre>
 * upload succeeded          -> TEMPORARY
 * attached to an aggregate  -> ACTIVE
 * removed or replaced       -> PENDING_DELETE
 * provider deletion ok      -> database record removed
 * </pre>
 */
public enum MediaStatus {
    TEMPORARY,
    ACTIVE,
    PENDING_DELETE
}
