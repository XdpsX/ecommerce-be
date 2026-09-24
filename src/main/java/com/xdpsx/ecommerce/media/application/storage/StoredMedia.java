package com.xdpsx.ecommerce.media.application.storage;

/**
 * Result of a successful upload.
 *
 * @param externalId provider identity, required later to delete the asset
 * @param url public URL of the stored asset
 */
public record StoredMedia(String externalId, String url) {}
