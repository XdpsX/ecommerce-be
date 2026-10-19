# Media direct upload FE handoff

New clients should use signed direct upload. The existing multipart `POST /media/image-upload` remains available as a compatibility path and is not deprecated.

Generate the backoffice client from `/v3/api-docs/backoffice`. Media operations require ADMIN. The backend scopes intent, complete, status, and delete to the authenticated uploader; a different uploader receives `404`.

## Upload and attach

1. Request an intent with metadata only:

   ```http
   POST /media/upload-intents
   Authorization: Bearer <backend-token>
   Content-Type: application/json

   {"purpose":"PRODUCT_IMAGE","filename":"front.png","contentType":"image/png","sizeBytes":204800}
   ```

   Example response:

   ```json
   {
     "mediaId": "media-123",
     "intentExpiresAt": "2026-10-16T12:00:00Z",
     "uploadAuthorizationExpiresAt": "2026-10-16T12:00:00Z",
     "upload": {
       "provider": "CLOUDINARY",
       "method": "POST",
       "url": "https://api.cloudinary.com/v1_1/example/image/upload",
       "fields": {"api_key":"public-key","timestamp":"...","public_id":"product/media-123","signature":"..."}
     },
     "status": {"upload":"PENDING_UPLOAD","processing":"PENDING","attachment":"TEMPORARY"}
   }
   ```

   The timestamps are UTC. `intentExpiresAt` describes the application intent; `uploadAuthorizationExpiresAt` is the provider signature expiry. Use the actual fields returned by the API.

2. Send the file directly to the returned provider URL. Preserve every signed field name and value, append the file, and use the returned method. Do not send the backend JWT to Cloudinary and do not set multipart `Content-Type` yourself; the browser must add its boundary.

   ```ts
   async function uploadToProvider(intent: MediaUploadIntentResponse, file: File) {
     const body = new FormData();
     for (const [name, value] of Object.entries(intent.upload.fields)) {
       body.append(name, value);
     }
     body.append("file", file);

     return fetch(intent.upload.url, {
       method: intent.upload.method,
       body,
     });
   }
   ```

3. Call `POST /media/{mediaId}/complete` without sending provider URL, public ID, or provider response metadata as proof. The backend inspects the public ID already stored with the intent.

4. After complete returns `uploadStatus: "UPLOADED"`, save `mediaId` through the Catalog API when its purpose, uploader, ownership, and lifecycle guards pass. `PROCESSING` and `FAILED` do not block attachment. Do not wait for READY or for UI polling to finish before saving Catalog data.

5. Poll `GET /media/{mediaId}` for UI updates. Render `originalUrl` as a fallback while processing; it may be null before verification. Use named `variants` when they become available at `UPLOADED + READY`. Variants and Catalog save are independent.

Purpose/resource mapping is `CATEGORY_IMAGE` / `category`, `BRAND_LOGO` / `brand`, `PRODUCT_IMAGE` / `product`, and `PRODUCT_DESCRIPTION_IMAGE` / `product-description`. Backend image validation and purpose policy remain authoritative. The legacy multipart shape includes `caption`; the direct intent contract does not.

## Polling, cancellation, and recovery

- Poll every 2 seconds for at most 60 seconds in one UI wait. Stop when processing is terminal (`READY` or `FAILED`) or attachment is `PENDING_DELETE`. Timeout only ends that wait: keep the original fallback, allow a later status check, and never mutate backend state to `FAILED` or block Catalog save because the wait timed out.
- Removing, undoing, or cancelling an editor change must not call Media delete for an image already referenced by persisted content. Description-image reference synchronization and its lifecycle are not implemented; see the follow-up boundary below.
- If browser/provider upload times out or reports a network/CORS error, the upload result may be unknown. Read status, then try complete on the same intent while its lifecycle and completion window still allow it. A status GET alone does not inspect Cloudinary. Do not create new intents in an unbounded retry loop.
- Repeating complete is guaranteed only while the Media remains `TEMPORARY`. Once attached, use GET status. DELETE returning `204` claims the Media for cleanup; it does not guarantee immediate provider deletion.
- Provider HTTP/network errors are distinct from backend ProblemDetail responses and have no backend application error code.

| Backend response | Meaning and client action |
| --- | --- |
| `409 MEDIA_UPLOAD_CONFLICT` | Several conditions share this code, including missing provider object or lifecycle conflict. Read status; do not retry every 409. A bounded same-intent complete retry is reasonable only after an uncertain upload while the intent remains completable. After attachment, use GET; stop for `PENDING_DELETE`. |
| `409 MEDIA_UPLOAD_EXPIRED` | Completion window ended and the intent was claimed for cleanup. Stop using the old intent; start a new upload only when the user chooses to try again. |
| `422 MEDIA_ASSET_INVALID` | Provider asset metadata or signed identity failed validation. Do not retry complete with the same file/intent; let the user retry with a new upload. |
| `503 MEDIA_PROVIDER_UNAVAILABLE` | Backend could not inspect Cloudinary. Retry complete with bounded backoff on the same intent while its lifecycle/window permit; check status as needed. Do not assume a new intent is required. |
| Catalog `409 MEDIA_ATTACHMENT_CONFLICT` | An attachment ownership/lifecycle guard failed. This does not mean image processing is still pending; do not retry the Catalog save waiting for variants. |

The response carries no public subcode that distinguishes every cause of `MEDIA_UPLOAD_CONFLICT`; clients must not infer one.

## Current boundaries

- Legacy `ACTIVE` Media remains readable. Attachment mutations fail closed when its owner is missing or unresolved; run the read-only owner audit and review any repair mapping before rollout.
- Rich-text reference extraction/sanitization and description attachment synchronization are follow-up work. Keep stable `mediaId` references, but do not treat embedded description images as lifecycle-managed by this release.
- Signed `PENDING_DELETE` records remain tombstones for retention/reinspection. Automatic tombstone purge and orphan sweeping require a separate policy and are not included.
- Scheduler defaults for reconciliation and cleanup remain disabled.

## Rollout and rollback

Before switching a consumer, complete the environment migration check, owner audit/repair gate, Cloudinary account/webhook configuration, and browser smoke in [the media checklist](media-manual-test-checklist.md). Record the consumer origin and evidence. Verify browser CORS/preflight for the backend and Cloudinary, exact signed fields, absence of backend JWT and manually set multipart content type on the provider request, complete verification, normal and early webhook, fallback, variants, and Catalog save during PROCESSING. Test timeout and FAILED fallback without changing backend state.

Switch one consumer at a time and monitor upload/processing/cleanup backlogs using [the cleanup runbook](media-attachment-cleanup-runbook.md). Worker activation is a separate controlled rollout step on one identified worker after provider inspection/deletion smoke. Keep the multipart path available. Roll back a consumer to multipart if needed; disable a worker during investigation. No direct-upload feature flag is assumed to exist, and rollback does not repair legacy owner conflicts.
