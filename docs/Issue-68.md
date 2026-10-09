## Context

The current media module already provides a provider-neutral `Media` abstraction and uses Cloudinary as the storage adapter.

At the moment, image transformation is performed synchronously as part of the upload flow. We want to evolve this into a hybrid processing strategy:

- **Common/predictable variants** → pre-generate with Cloudinary **Eager Async**
- **Rare/dynamic variants** → generate with Cloudinary **Lazy/On-demand transformations**

Cloudinary remains responsible for physical storage, transformation, CDN, and caching. Solaza remains responsible for the logical media lifecycle.

## Design decisions

### Separate media attachment and processing lifecycles

The current `MediaStatus` represents the attachment/cleanup lifecycle:

```text
TEMPORARY → ACTIVE → PENDING_DELETE
```

Async processing introduces a separate lifecycle:

```text
PROCESSING → READY
           ↘ FAILED
```

These states should not be merged into a single enum because they represent independent concerns.

A media asset may, for example, be:

```text
TEMPORARY + PROCESSING
TEMPORARY + READY
ACTIVE    + PROCESSING
ACTIVE    + READY
```

Introduce separate attachment and processing statuses.

### Do not persist MediaVariant records for now

Common variants should be represented as transformation presets in code/config rather than database records.

Variant URLs can be deterministically generated from:

```text
Media.externalId + transformation preset
```

The same URL-generation mechanism can be used for both eager-generated and lazy-generated variants.

A `MediaVariant` table should only be introduced later if the application needs to persist per-variant metadata, URLs, or lifecycle state.

## Proposed flow

### Upload

```text
Client
  ↓
Media API
  ↓
Cloudinary upload original
  + eager common transformations
  + eager_async = true
  ↓
Cloudinary returns upload response
  ↓
Persist Media
  attachmentStatus = TEMPORARY
  processingStatus = PROCESSING
  ↓
Return response to client
```

### Async processing completion

```text
Cloudinary
  ↓
Webhook
  ↓
Verify signature
  ↓
Resolve Media by provider identity
  ↓
PROCESSING → READY / FAILED
```

### Lazy transformations

For uncommon/dynamic variants, generate a Cloudinary transformation URL from the media external ID and requested transformation. Cloudinary generates the derived asset on first request and serves/caches it afterward.

## Tasks

- [ ] Split the current media status into attachment lifecycle and processing lifecycle
- [ ] Add processing failure information where useful
- [ ] Define common transformation presets for supported media purposes
- [ ] Replace the current synchronous upload transformation with Cloudinary eager async transformations
- [ ] Add a Cloudinary webhook endpoint for processing completion
- [ ] Verify Cloudinary webhook signatures
- [ ] Make webhook processing idempotent and safe for duplicate delivery
- [ ] Update media processing state on success/failure
- [ ] Add URL generation for predefined/common variants
- [ ] Support lazy/on-demand transformation URLs for uncommon variants
- [ ] Preserve provider-specific transformation details inside the Cloudinary infrastructure adapter
- [ ] Cover upload, processing, webhook, failure, and duplicate-webhook scenarios with tests

## Out of scope

- A separate file-processing service or worker
- A queue owned by Solaza for image transformation
- A `MediaVariant` persistence table
- Moving media processing away from Cloudinary

These can be reconsidered later if processing ownership or infrastructure requirements change.
