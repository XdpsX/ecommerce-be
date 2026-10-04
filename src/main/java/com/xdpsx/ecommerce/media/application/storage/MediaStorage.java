package com.xdpsx.ecommerce.media.application.storage;

/**
 * Provider-neutral storage port used by the Media application layer.
 *
 * <p>Application code must depend on this port instead of a concrete storage provider so that
 * provider coupling stays inside the adapter (folder layout, transformations, error semantics).
 */
public interface MediaStorage {

    /**
     * Uploads the given file and returns the provider identity plus the public URL.
     *
     * @throws MediaStorageException when the provider rejects the upload or returns an unusable response
     */
    StoredMedia upload(MediaUploadCommand command);

    /**
     * Permanently removes the stored asset.
     *
     * <p>Implementations must treat "already absent" as success and must only signal failure when the
     * asset may still exist at the provider, because callers rely on this contract to decide whether a
     * cleanup can be considered complete.
     *
     * @throws MediaStorageException when the asset could not be confirmed as deleted
     */
    void delete(String externalId);
}
