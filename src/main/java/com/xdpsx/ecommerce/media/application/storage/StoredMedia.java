package com.xdpsx.ecommerce.media.application.storage;

/**
 * Result of a successful upload.
 *
 * @param externalId provider identity, required later to delete the asset
 * @param url public URL of the stored asset
 * @param processingReference provider-neutral correlation value for asynchronous processing
 */
public record StoredMedia(String externalId, String url, String processingReference) {
    public StoredMedia(String externalId, String url) {
        this(externalId, url, null);
    }
}
