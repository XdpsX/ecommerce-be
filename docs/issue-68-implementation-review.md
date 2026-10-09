# Issue #68 Implementation Review Guide

Issue: [Issue #68](Issue-68.md)

Approved plan: [Async media processing plan](m1/issue-68-async-media-processing-plan.md)

Branch: `feat/68-async-media-processing`

## Goal and implemented scope

Issue #68 moves common Cloudinary image transformations out of the synchronous upload path and into asynchronous eager processing. The application still owns authorization, attachment state, processing state, failure information, and the API contract; Cloudinary owns storage, transformation, CDN delivery, and caching.

The implementation includes:

- independent attachment and processing lifecycles;
- purpose-specific eager transformation presets;
- Cloudinary `eager_async` upload with `batch_id` correlation;
- an authenticated, idempotent eager-completion webhook;
- deterministic named variant URLs on the Media upload response;
- migration of existing rows to processing state `READY`;
- configuration validation and local tunnel documentation;
- representative domain, application, adapter, API, concurrency, and migration tests.

It does not add a queue, worker, `MediaVariant` database table, retry/reconciliation mechanism, or consumer-facing dynamic transformation API.

## Behavior before and after

### Before

- `MediaStatus` combined the attachment lifecycle in one `status` column.
- Upload requested one synchronous Cloudinary transformation based on the media purpose's minimum width.
- A successful upload was persisted only after that synchronous provider call completed.
- The upload API returned the original URL without named variants.
- There was no processing lifecycle, failure reason, webhook, or asynchronous correlation value.

### After

- `MediaAttachmentStatus` retains `TEMPORARY -> ACTIVE -> PENDING_DELETE` independently from processing.
- `MediaProcessingStatus` starts new uploads at `PROCESSING` and permits one terminal transition to `READY` or `FAILED`.
- Upload sends all approved variants as Cloudinary eager transformations with `eager_async=true` and a validated HTTPS notification URL.
- The upload response's Cloudinary `batch_id` is stored as the provider-neutral `processingReference` and is used to resolve eager notifications.
- The Media upload response keeps `url` as the original URL and adds purpose-appropriate names under `variants`.
- Product, category, and brand read APIs keep their existing `ViewMediaDTO` contract and do not expose variants.
- Existing rows are migrated to `processing_status = READY` while preserving their attachment status.

## Important decisions and invariants

### Two independent lifecycles

Attachment and processing do not block each other. A media record may be `TEMPORARY + PROCESSING`, `ACTIVE + PROCESSING`, or any valid attachment state combined with a terminal processing state. Cleanup and attachment operations continue to depend only on `attachmentStatus`.

### First terminal processing result wins

- `PROCESSING + success -> READY`
- `PROCESSING + failure -> FAILED`
- duplicate terminal notifications are acknowledged without changing state;
- a later conflicting terminal notification is logged and does not overwrite the first result;
- failure reasons are sanitized and limited to 500 characters.

The webhook handler takes a pessimistic write lock by processing reference so competing terminal deliveries serialize at the database row.

### Correlation uses Cloudinary `batch_id`

Cloudinary Java SDK `2.4.0` upload-map deserialization is covered by a test that includes `batch_id`. The same value is accepted from the eager webhook payload and stored in the provider-neutral `processing_reference` column. `externalId` remains the asset identity used for deletion and URL generation.

### Presets are centralized adapter policy

The approved preset-to-purpose mapping lives in the Cloudinary infrastructure adapter. Domain and application code deal only with semantic `MediaVariant` values. Eager generation and delivery URL generation reuse the same transformation definitions.

The transformations retain the source/original format and use `q_auto`. They deliberately do not use `f_auto`, because browser-dependent automatic format selection does not represent one deterministic eager asset.

### Description-image validation

`PRODUCT_DESCRIPTION_IMAGE` has no minimum source width. The service still decodes and validates that the upload is an image; it skips only the width comparison. Its `PRODUCT_CONTENT` variant uses `c_limit,w_1200,q_auto`, preserving aspect ratio without cropping.

### Webhook security and retry behavior

- Spring Security permits only `POST /webhooks/cloudinary/eager` without user authentication.
- The controller captures the raw body and verifies `X-Cld-Signature` and `X-Cld-Timestamp` before parsing JSON.
- Timestamp tolerance defaults to two hours and is configurable.
- An invalid signature returns `401`; a signed malformed event returns `400`.
- A valid event whose media row is not visible yet returns `503`, allowing Cloudinary delivery retries to cover the upload-response/database-insert race.
- Applied, duplicate, and conflicting terminal events return `200`.

### Configuration is fail-fast

`media.cloudinary.eager-notification-url` must be a non-blank HTTPS URL. Local end-to-end testing uses a public HTTPS tunnel; there is no webhook-disabled local processing mode.

## Changed components by responsibility

| Responsibility | Main components | What changed |
| --- | --- | --- |
| Media API | `MediaController`, `MediaControllerApi`, `UploadedMediaDTO` | Upload now returns original URL plus named variants. Catalog read DTOs remain unchanged. |
| Application flow | `MediaServiceImpl`, `MediaService`, `CloudinaryEagerWebhookHandler` | Upload persists `TEMPORARY + PROCESSING`; webhook applies terminal transitions transactionally. |
| Domain model | `Media`, `MediaAttachmentStatus`, `MediaProcessingStatus`, `MediaPurpose` | Splits lifecycles, removes the old `MediaStatus`, adds failure/correlation state and description-image purpose. |
| Provider-neutral ports | `StoredMedia`, `MediaVariant`, `MediaUrlGenerator` | Carries asynchronous correlation and exposes only semantic variant names. |
| Cloudinary adapter | `CloudinaryMediaStorage`, `CloudinaryMediaTransformations`, `CloudinaryMediaUrlGenerator`, `CloudinaryUploadResponse`, `CloudinaryUploader` | Configures eager async transformations, maps `batch_id`, and generates deterministic variant URLs. |
| Webhook boundary | `CloudinaryWebhookController`, `NotificationSignatureVerifier`, `CloudinaryNotificationSignatureVerifier`, `CloudinaryEagerNotification` | Verifies the raw signed notification and parses provider payloads only after authentication. |
| Persistence | `MediaRepository`, `changeset-26.sql` | Renames attachment status, adds processing fields, backfills legacy rows, and locks webhook lookup. |
| Configuration/security | `CloudinaryMediaProperties`, `SecurityConfig`, `SecurityConstants`, application YAML, `.env.example` | Validates the public webhook URL, configures tolerance, and permit-lists the exact webhook path. |
| Existing attachment consumers | Product media attachment and media cleanup flows | Replaces old status references without changing attachment or cleanup behavior. |

## Recommended code-reading order

Follow this order to review one complete upload and callback cycle.

1. **API contract and resource parsing**
   - `src/main/java/com/xdpsx/ecommerce/media/api/MediaController.java`
   - Read `createMedia` to see resource-to-purpose parsing and the `UploadedMediaDTO` response.
   - Then inspect `media/api/dto/UploadedMediaDTO.java` and confirm that `variants` is additive while `url` remains the original.

2. **Upload orchestration and compensation**
   - `src/main/java/com/xdpsx/ecommerce/media/application/MediaServiceImpl.java`
   - Read `createMedia`, `validateImageSize`, and `cleanupQuietly`.
   - Confirm the order: validate -> upload -> persist `TEMPORARY + PROCESSING` -> generate response URLs, and confirm persistence failure deletes the uploaded orphan.

3. **Domain lifecycle rules**
   - `src/main/java/com/xdpsx/ecommerce/media/domain/Media.java`
   - Read `activate`, `markPendingDeletion`, `markProcessingReady`, and `markProcessingFailed`.
   - Compare them with `MediaAttachmentStatus` and `MediaProcessingStatus`; verify that terminal processing state cannot be overwritten.

4. **Semantic variant catalog**
   - `src/main/java/com/xdpsx/ecommerce/media/application/storage/MediaVariant.java`
   - Read `forPurpose` and confirm the five stable API names and their purpose mapping.
   - Inspect `MediaPurpose.PRODUCT_DESCRIPTION_IMAGE` and its absent minimum-width policy.

5. **Cloudinary upload and transformation mapping**
   - `src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryMediaStorage.java`
   - Read `upload` and `uploadOptions`; verify `eager`, `eager_async`, notification URL, required `batch_id`, and orphan cleanup.
   - Continue with `CloudinaryMediaTransformations.forVariant` to verify exact dimensions, crop, gravity, and `q_auto`.
   - Read `CloudinaryUploadResponse.batchId` and `CloudinaryUploader.uploadFile` to see provider response mapping.

6. **Named delivery URLs**
   - `src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryMediaUrlGenerator.java`
   - Read `generateVariants` and confirm it reuses the centralized transformation catalog rather than accepting arbitrary transformation parameters.

7. **Webhook authentication boundary**
   - `src/main/java/com/xdpsx/ecommerce/media/api/CloudinaryWebhookController.java`
   - Read `eagerNotification` and verify signature checking occurs before JSON parsing or state mutation.
   - Continue with `CloudinaryNotificationSignatureVerifier.isValid` to inspect header validation, clock tolerance, and SDK signature verification.

8. **Webhook payload and application transition**
   - `src/main/java/com/xdpsx/ecommerce/media/infrastructure/cloudinary/CloudinaryEagerNotification.java`
   - Read `outcome` and `failureReason` to understand success/failure payload handling.
   - Then read `CloudinaryEagerWebhookHandler.handle` to follow correlation lookup, the processing lock, first-terminal-wins behavior, logging, and retry response selection.

9. **Persistence and migration**
   - `src/main/java/com/xdpsx/ecommerce/media/persistence/MediaRepository.java`
   - Inspect `findByProcessingReferenceForUpdate` and the attachment-only cleanup/attachment queries.
   - Then read `src/main/resources/db/changelog/changesets/changeset-26.sql` in execution order: rename -> add nullable columns -> backfill -> enforce non-null processing status -> add unique correlation constraint.

10. **Representative tests**
    - Start with `MediaServiceImplTest` for upload state, validation, compensation, and description images.
    - Read `CloudinaryMediaStorageTest` and `CloudinaryMediaUrlGeneratorTest` for exact provider options and preset strings.
    - Read `CloudinaryNotificationSignatureVerifierTest`, `CloudinaryWebhookControllerTest`, and `CloudinaryEagerWebhookHandlerTest` for authentication, payload behavior, duplicates, conflicts, and failure sanitization.
    - Finish with `MediaAttachmentConcurrencyTest` and `MediaProcessingMigrationTest` for locking and MySQL migration behavior.

## Plan deviations and clarified decisions

- The original issue mentioned lazy/on-demand variants. The approved plan removed that consumer API from #68 because no current business requirement needs arbitrary transformations.
- Correlation was gated on actual provider behavior. The implementation selected `processing_reference = batch_id` and added adapter tests for upload deserialization and documented eager payload shapes.
- The initial preset proposal used `format=auto`. The implementation keeps source/original format because `f_auto` is request-client dependent and cannot identify one eagerly generated representation.
- Named variants are exposed only by the Media upload API. Existing product, category, and brand read APIs intentionally remain unchanged.

## Verification

The final review run executed this focused regression set:

```powershell
.\mvnw.cmd "-Dtest=MediaTest,MediaServiceImplTest,MediaControllerTest,CloudinaryWebhookControllerTest,CloudinaryMediaPropertiesTest,CloudinaryMediaStorageTest,CloudinaryMediaUrlGeneratorTest,CloudinaryNotificationSignatureVerifierTest,CloudinaryEagerWebhookHandlerTest,CloudinaryUploaderTest,MediaCleanUpSchedulerTest,MediaAttachmentConcurrencyTest,SecurityBoundaryTest,OpenApiDocumentationTest,ProductServiceImplTest,BrandServiceImplTest,CategoryServiceImplTest" test
```

Result: 170 tests passed with no failures, errors, or skips.

Formatting and diff checks:

```powershell
.\mvnw.cmd spotless:check
git diff --check HEAD
```

Result: both passed.

## Skipped checks and remaining risks

- `MediaProcessingMigrationTest` was not executed because the local Docker daemon was unavailable to Testcontainers. The migration remains the main unverified environment-dependent check.
- A webhook can still arrive before the media transaction commits. Returning `503` relies on Cloudinary's finite retry policy; there is intentionally no application reconciliation job in this issue.
- Media can remain `PROCESSING` after all provider deliveries are exhausted. The issue provides persisted state and logs for diagnosis, but no recovery mechanism.
- Preset dimensions are initial UI defaults and may change once the frontend requirements are concrete.
- Processing readiness is tracked for the eager batch as a whole, not independently per variant.

## Follow-up candidates

Create a separate issue only if operational evidence justifies one of these additions:

- scheduled reconciliation of stuck processing records;
- provider status checks;
- manual or automatic retry/reprocess operations;
- per-variant persistence and lifecycle;
- a bounded dynamic transformation API.
