# Issue #68 Plan: Cloudinary async eager media processing and named variants

Source: [Issue #68](../Issue-68.md)

Branch: `feat/68-async-media-processing`

Status: approved for implementation with the decisions recorded below. Correlation must pass the Cloudinary payload gate described below before the database migration is finalized.

## Recommended scope and approach

Keep Cloudinary responsible for storing originals, generating derived assets, serving them through its CDN, and caching transformations. Keep the application responsible for authorization, attachment lifecycle, processing lifecycle, provider-neutral named variants, and webhook-driven state changes.

Implement the change as one vertical media flow:

1. split attachment state from processing state and migrate existing rows;
2. upload the original with purpose-specific eager presets and `eager_async=true`;
3. persist the provider identity and, only if required by the verified webhook contract, a provider processing reference with `PROCESSING` state;
4. accept and authenticate the Cloudinary eager notification;
5. apply an idempotent `PROCESSING -> READY | FAILED` transition;
6. expose deterministic URLs for the approved named variants through a provider-neutral port whose Cloudinary transformation mapping remains in the adapter.

Do not add a queue, worker service, `MediaVariant` table, another storage provider, or a consumer-facing dynamic/lazy transformation API in this CR.

## Current behavior

- `Media.attachmentStatus` and `MediaAttachmentStatus` model only attachment and cleanup: `TEMPORARY -> ACTIVE -> PENDING_DELETE`.
- `MediaServiceImpl.createMedia` validates the image, calls `MediaStorage.upload`, then persists a `TEMPORARY` row. A failed insert compensates by deleting the uploaded asset.
- `CloudinaryMediaStorage` sends a synchronous upload `transformation` based on `MediaPurpose.minWidth()` and returns the original provider identity and URL.
- `Media.externalId` already stores Cloudinary's `public_id`; `Media.url` stores the original delivery URL.
- Attachment, delete, expiry cleanup, and concurrency queries all depend on the current `status` field.
- `ViewMediaDTO` exposes only `id`, `caption`, `contentType`, and the original `url`.
- There is no webhook endpoint, processing state, processing failure detail, eager batch correlation, or provider-neutral URL-generation port.

## Proposed model and invariants

### Attachment lifecycle

Rename the Java concept to `MediaAttachmentStatus`:

```text
TEMPORARY -> ACTIVE -> PENDING_DELETE
```

Keep the existing transition rules:

- only `TEMPORARY` media can be attached;
- deletion through the Media API can claim only `TEMPORARY` media;
- cleanup may delete the database row only after provider deletion is confirmed;
- processing state does not prevent attachment or cleanup because the two lifecycles are independent.

### Processing lifecycle

Add `MediaProcessingStatus`:

```text
PROCESSING -> READY
PROCESSING -> FAILED
```

Domain operations should enforce:

- a success notification changes `PROCESSING` to `READY` and clears failure detail;
- a failure notification changes `PROCESSING` to `FAILED` and stores a sanitized, bounded failure reason;
- repeating the same terminal outcome is a no-op;
- a duplicate or stale notification must never move `READY` or `FAILED` back to `PROCESSING`;
- a conflicting terminal notification is acknowledged without overwriting the first accepted terminal result, and is logged for investigation.

### Correlation gate and persistence fields

Do not make Cloudinary's `batch_id` a domain invariant before verifying the exact upload response and eager notification shape used by Cloudinary Java SDK `2.4.0`. Before finalizing the migration, add captured success and failure fixtures (or perform a narrow sandbox characterization) and determine whether both sides expose the same stable asset identity:

- prefer the existing unique `external_id` when the upload response and eager webhook both contain that identity directly and unambiguously;
- otherwise persist a nullable, unique, provider-neutral `processing_reference VARCHAR(255)` whose Cloudinary adapter value is the eager `batch_id`;
- do not parse a public ID from a derived URL or use transformation order as correlation;
- keep `batch_id` parsing and naming inside the Cloudinary adapter even when its value is stored as `processing_reference`.

Cloudinary's current documentation explicitly describes `batch_id` as the tracking value shared by the async eager upload response and eager notification. It does not make a stable asset identity part of the minimal eager notification contract, so `processing_reference = batch_id` remains the expected fallback, but the schema decision follows the verified fixture rather than preceding it.

Add or rename columns through a new Liquibase changeset after that gate:

- rename `media.status` to `attachment_status`;
- add non-null `processing_status VARCHAR(32)`;
- add nullable `processing_failure_reason VARCHAR(500)`;
- add nullable, unique `processing_reference VARCHAR(255)` only when the verified contract requires a separate correlation value.

Backfill all existing media with `processing_status = 'READY'` because their synchronous upload path completed before persistence. New async uploads start as `PROCESSING`.

Resolve eager notifications by the selected persisted correlation key: `external_id` when verified as present and stable, otherwise `processing_reference`. The existing `external_id` remains the stable provider asset identity used for deletion and delivery URL generation regardless of the webhook strategy.

## Provider boundary

Extend the storage result without leaking raw Cloudinary payloads into application code. `StoredMedia` should carry:

- provider asset identity (`externalId`);
- original secure URL;
- an optional provider-neutral asynchronous processing reference when the correlation gate requires one.

Introduce a provider-neutral URL-generation port, for example `MediaUrlGenerator`, that accepts only a predefined semantic variant identifier. It must not accept Cloudinary transformation strings or arbitrary transformation parameters.

Keep these details inside the Cloudinary adapter:

- folder mapping;
- eager transformation objects;
- `eager_async` and `eager_notification_url` upload options;
- mapping from semantic variants to Cloudinary transformations;
- Cloudinary URL construction and signing policy;
- parsing of Cloudinary notification payload fields.

There is no current business requirement for consumers to request uncommon/dynamic variants. Cloudinary may retain on-demand transformation capability internally, but #68 will not expose a lazy transformation endpoint or arbitrary resize contract.

## Approved common preset catalog

The initial presets are:

| Media purpose | Preset | Size | Crop | Gravity | Format | Quality |
| --- | --- | --- | --- | --- | --- | --- |
| `PRODUCT_IMAGE` | `PRODUCT_CARD` | 600 x 600 | `fill` | `auto` | source/original | `auto` |
| `PRODUCT_IMAGE` | `PRODUCT_DETAIL` | 1200 x 1200 | `fit` | none | source/original | `auto` |
| `PRODUCT_DESCRIPTION_IMAGE` | `PRODUCT_CONTENT` | maximum width 1200 | `limit` | none | source/original | `auto` |
| `CATEGORY_IMAGE` | `CATEGORY_CARD` | 600 x 400 | `fill` | `auto` | source/original | `auto` |
| `BRAND_LOGO` | `BRAND_LOGO` | 400 x 400 | `fit` | none | source/original | `auto` |

Purpose-to-preset mapping:

```text
PRODUCT_IMAGE
|-- PRODUCT_CARD
`-- PRODUCT_DETAIL

PRODUCT_DESCRIPTION_IMAGE
`-- PRODUCT_CONTENT

CATEGORY_IMAGE
`-- CATEGORY_CARD

BRAND_LOGO
`-- BRAND_LOGO
```

`PRODUCT_DESCRIPTION_IMAGE` must be added to `MediaPurpose` and accepted through the existing media upload purpose parsing. Use `product-description` as its external resource value unless the API naming is changed before implementation. It does not introduce a minimum source width: represent that explicitly as an absent/nullable minimum-width policy and let validation skip the check. The current flow already supports `minWidth() == null`; preserve or clarify that contract and do not encode “no minimum” as `0`. `PRODUCT_CONTENT` preserves aspect ratio, does not crop, and limits output width to 1200 pixels.

These presets are initial UI defaults, not domain invariants. Keep their Cloudinary transformation definitions centralized so later UI-driven adjustments do not change media lifecycle code or database schema.

## Approved consumer/API contract

Keep `url` as the original/source URL and add named variant URLs under a `variants` object on the media upload response only. Available entries depend on `MediaPurpose`:

```json
{
  "id": "...",
  "url": "https://.../original.jpg",
  "variants": {
    "productCard": "https://.../product-card...",
    "productDetail": "https://.../product-detail..."
  }
}
```

Use these stable API names: `productCard`, `productDetail`, `productContent`, `categoryCard`, and `brandLogo`. Do not expose Cloudinary syntax or arbitrary transformation parameters. No lazy transformation API is part of #68.

The upload endpoint returns `UploadedMediaDTO` with the five fields above. Catalog read APIs keep their existing `ViewMediaDTO`/URL contracts and do not expose `variants` in nested brand, category, product, or storefront responses.

## Webhook flow and security

Add a dedicated infrastructure-facing endpoint, proposed as:

```text
POST /webhooks/cloudinary/eager
```

The endpoint must be permit-listed in Spring Security but authenticate every request using Cloudinary's notification signature before parsing or applying business state.

Processing sequence:

1. capture the exact raw request body;
2. require `X-Cld-Signature` and `X-Cld-Timestamp`;
3. reject timestamps outside a configurable tolerance whose default is two hours (`media.cloudinary.webhook-timestamp-tolerance=2h`);
4. verify the signature over the raw body plus timestamp using the configured Cloudinary API secret and constant-time comparison, preferably through the Cloudinary SDK when its Java API supports the documented operation;
5. parse only after signature verification and tolerate unknown JSON fields;
6. require the eager notification type, the correlation field selected by the correlation gate, and a recognized success/failure outcome;
7. update the matching `Media` in a short transaction with a pessimistic lock or conditional update;
8. return `200 OK` for an applied or duplicate terminal event;
9. return a non-200 response for a valid event whose media row is not visible yet, allowing Cloudinary's documented retries to cover the upload-response/database-insert race.

Invalid signatures and malformed payloads must not mutate state or disclose the configured secret. Logs should include safe values such as the selected correlation reference or media ID, never the raw signature or secret.

Cloudinary documents that eager async upload responses and eager notifications share a `batch_id`, notification signatures use the raw body and timestamp headers, and non-200 webhook responses receive three additional attempts. The repository currently uses Cloudinary Java SDK `2.4.0`; verify captured payload fields and SDK map handling before choosing the persistence correlation key:

- <https://cloudinary.com/documentation/notifications>
- <https://cloudinary.com/documentation/notification_signatures>
- <https://cloudinary.com/documentation/eager_and_incoming_transformations>

## Ordered implementation steps

1. **Characterize Cloudinary correlation.** Against Cloudinary Java SDK `2.4.0`, capture or fixture the async eager upload response and success/failure webhook payloads. Choose `external_id` only if the same stable asset identity is directly present on both sides; otherwise choose `processing_reference` backed by `batch_id`. Record the selected fixture fields in adapter tests.
2. **Add the database migration.** Create the next immutable Liquibase changeset, backfill existing rows to `READY`, add constraints/indexes, include `processing_reference` only if selected in step 1, and include the changeset from `db.changelog-master.yaml`.
3. **Split the domain states.** Use `MediaAttachmentStatus` for attachment lifecycle, add `MediaProcessingStatus`, add the failure field and only the correlation field selected in step 1, and implement idempotent terminal transition methods on `Media`.
4. **Update existing attachment consumers.** Adapt repository queries, cleanup scheduler, media service, catalog attachment flows, builders, fixtures, and tests without changing their attachment behavior.
5. **Define the approved semantic variants and validation policy.** Add `PRODUCT_CARD`, `PRODUCT_DETAIL`, `PRODUCT_CONTENT`, `CATEGORY_CARD`, and `BRAND_LOGO`; map them to purposes exactly as documented above. Add `PRODUCT_DESCRIPTION_IMAGE` to `MediaPurpose`, model its minimum width as absent/nullable, and make the validator skip only when the policy is absent. Do not use `0` as a sentinel. Avoid embedding Cloudinary classes in domain or application packages.
6. **Change the Cloudinary upload adapter.** Replace the synchronous `transformation` option with purpose-specific `eager` transformations, set `eager_async=true`, supply the configured eager notification URL, return the correlation value selected in step 1, and retain existing orphan cleanup behavior.
7. **Persist processing state.** Save new uploads as `TEMPORARY + PROCESSING` with the selected correlation value when a separate value is required. Preserve compensation when persistence fails.
8. **Implement signature verification.** Encapsulate raw-body/timestamp verification in the Cloudinary infrastructure package with configurable clock tolerance and testable clock/crypto boundaries where needed.
9. **Implement webhook handling.** Add a thin controller, provider payload DTO, and transactional application handler that resolves by the selected correlation key and applies idempotent success/failure transitions.
10. **Update security configuration.** Permit the exact webhook path only; keep all existing Media API authorization unchanged.
11. **Implement named URL generation.** Generate only the approved named variant URLs from `externalId` through the provider-neutral port and expose them through `UploadedMediaDTO.variants` on the media upload API; preserve `UploadedMediaDTO.url` and all catalog `ViewMediaDTO.url` values as the original URL.
12. **Add processing observability.** Make processing state and timestamps diagnosable through the persisted row and safe structured logs. Do not add a reconciliation query, scheduler, retry endpoint, or recovery mechanism.
13. **Update API documentation and local configuration.** Document the public webhook URL, the configurable two-hour timestamp tolerance, the additive `variants` response, and example environment variables without adding secrets. Document a tunnel as the supported local flow for end-to-end async testing; unit and integration tests use mocked upload responses and signed webhook fixtures. Do not add a webhook-disabled local processing mode or tunneling infrastructure.

## Likely affected files and components

- `media/domain/Media.java`
- `media/domain/MediaAttachmentStatus.java`
- `media/domain/MediaPurpose.java` (add `PRODUCT_DESCRIPTION_IMAGE`)
- new attachment and processing status enums
- `media/application/MediaServiceImpl.java`
- `media/application/storage/MediaStorage.java`, `StoredMedia.java`, and new URL-generation types
- `media/infrastructure/cloudinary/CloudinaryMediaStorage.java`
- `media/infrastructure/cloudinary/CloudinaryUploadResponse.java`
- new Cloudinary webhook verification/payload adapter classes
- new webhook controller and application handler
- `media/persistence/MediaRepository.java`
- `media/infrastructure/scheduling/MediaCleanUpScheduler.java`
- catalog services/mappers that attach or render media URLs
- `config/security/SecurityConstants.java` or the equivalent exact matcher configuration
- `application.yml`, profile configuration, and `.env.example`
- a new Liquibase changeset and `db.changelog-master.yaml`
- focused media domain, application, controller, Cloudinary adapter, and migration tests

## Smallest meaningful test set

### Main behavior

- Upload maps a purpose to the approved eager presets, sends `eager_async=true` and the notification URL, then persists `TEMPORARY + PROCESSING` with the correlation value selected by the verified contract when a separate value is needed.
- A valid signed success notification changes the matching media from `PROCESSING` to `READY`.
- Named variant URL generation produces the exact purpose-appropriate variant keys and deterministic Cloudinary URLs from `externalId`; the original `url` remains unchanged.
- `PRODUCT_CONTENT` uses `limit` with maximum width 1200, preserves aspect ratio, and performs no crop.

### Representative failures

- Invalid or stale webhook signatures are rejected without a database update.
- A signed failure notification records `FAILED` with bounded/sanitized failure information.
- A missing required correlation value, unusable provider response, or database insert failure preserves the current error/compensation behavior.
- `PRODUCT_DESCRIPTION_IMAGE` bypasses minimum-width rejection through an absent validation policy, not a numeric sentinel.

### Idempotency and compatibility boundaries

- Delivering the same success or failure webhook twice leaves one terminal state and returns success both times.
- Competing success/failure notifications serialize and do not overwrite the first terminal result.
- Correlation fixtures prove whether webhook lookup uses `external_id` or the separately persisted provider processing reference.
- Migration backfills legacy rows to `READY` while preserving `TEMPORARY`, `ACTIVE`, and `PENDING_DELETE` attachment states.
- Existing attachment concurrency and cleanup tests continue to pass after the status rename.

Avoid a live Cloudinary integration test in the normal suite. Mock the SDK boundary and use captured raw webhook fixtures. Keep the MySQL migration test behind the repository's existing Testcontainers setup.

## Verification commands

Run focused checks first:

```powershell
.\mvnw.cmd -Dtest=MediaTest,MediaServiceImplTest,CloudinaryMediaStorageTest,MediaCleanUpSchedulerTest test
.\mvnw.cmd -Dtest=MediaControllerTest,MediaAttachmentConcurrencyTest test
```

Add the new webhook, URL generation, and migration test classes to the focused commands once named. Then run:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
```

The full suite requires Docker for Testcontainers migration/concurrency tests. Report those checks as skipped or environment-blocked rather than treating missing Docker as a product failure.

## Compatibility and migration impact

- The database change is additive except for renaming `status` to `attachment_status`; all existing rows are backfilled before constraints become non-null. A `processing_reference` column is added only if the verified provider contract cannot safely reuse `external_id` for webhook correlation.
- Existing attachment semantics and original media URLs remain valid.
- Adding processing state and the named `variants` object to the media upload response is additive; do not remove or reinterpret the existing `url` field or expand catalog read DTOs in this CR.
- `PRODUCT_DESCRIPTION_IMAGE` is a new accepted media purpose and has only the `productContent` variant.
- Webhook configuration requires a publicly reachable HTTPS URL. A documented tunnel is the supported local flow for end-to-end async testing; automated tests mock the upload response and webhook delivery. Do not add a webhook-disabled local mode or tunneling infrastructure to the repository.
- A media row can remain `PROCESSING` if every webhook delivery is exhausted. #68 provides observability only; reconciliation and retry require a follow-up issue if this becomes an operational problem.

## Risks

- The eager notification may race the database insert. Resolving by the verified correlation key and returning non-200 for a temporarily unknown valid event relies on Cloudinary's finite retry window.
- Incorrect raw-body handling will invalidate otherwise legitimate webhook signatures.
- Presets may need adjustment after the UI exists; keeping them centralized avoids turning initial dimensions into domain invariants.
- Renaming the attachment status touches several catalog flows; accidental query omissions could allow active media deletion or prevent cleanup.
- A single media-level `READY` state means all required eager presets are treated as one batch outcome. Per-variant state would require the explicitly out-of-scope `MediaVariant` model.
- `FAILED` has no recovery path in the current issue. A manual reprocess/reconciliation capability may become necessary later.

## Approved processing decisions

### Failure detail

Store one nullable, sanitized failure reason on `Media` as `processing_failure_reason VARCHAR(500)`. Provider-specific/raw payloads are not part of the domain model and must not be persisted. They may be included in structured logs only when safe and useful for diagnostics.

### Terminal conflict policy

Use first terminal outcome wins:

```text
PROCESSING + success           -> READY
PROCESSING + failure           -> FAILED
READY      + duplicate success -> acknowledge, no-op
FAILED     + duplicate failure -> acknowledge, no-op
READY      + later failure     -> keep READY, log conflict
FAILED     + later success     -> keep FAILED, log conflict
```

Duplicate webhook delivery is idempotent. Conflicting terminal delivery never overwrites the accepted terminal state.

### Webhook timestamp tolerance

Default to two hours, following Cloudinary's documented example, and make it configurable:

```properties
media.cloudinary.webhook-timestamp-tolerance=2h
```

Verify both timestamp and signature before parsing and processing the event.

### Stuck processing recovery

Support observability only for media that remains in `PROCESSING` unexpectedly long. Persisted state, timestamps, and safe structured logs must make diagnosis possible, but #68 does not add scheduled reconciliation, provider status checks, manual retry, or automatic recovery. Create a follow-up issue if stuck processing becomes a measured operational problem.

## Explicitly out of scope

- application-owned queues or transformation workers;
- a separate processing service;
- `MediaVariant` persistence;
- replacing Cloudinary;
- direct browser-to-Cloudinary upload;
- a consumer-facing dynamic/lazy transformation API or arbitrary transformation parameters;
- automatic retries/reconciliation after Cloudinary exhausts webhook delivery;
- unrelated media, catalog, or security redesign.
