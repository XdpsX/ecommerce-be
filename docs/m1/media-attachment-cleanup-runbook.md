# Media Attachment Audit, Cleanup and Inventory Runbook

This runbook covers the PR E attachment ownership boundary and the opt-in cleanup worker. It does not authorize
bulk data repair or deletion of assets that have no Media row.

## Before enabling attachment writes

Run the audit with a database principal that has `SELECT` only. Record the environment, UTC timestamp, application
baseline and query output outside Git. Stop Catalog/media writes and rerun immediately before rollout; results are a
snapshot and can become stale while writes continue.

The first query works before and after changeset 014. It reports multiple aggregate references, dangling Media,
purpose/lifecycle disagreement and ACTIVE Media without a relation:

```sql
WITH refs AS (
    SELECT 'CATEGORY' AS owner_type, CAST(id AS CHAR(64)) AS owner_id,
           image_id AS media_id, 'CATEGORY_IMAGE' AS expected_purpose
    FROM categories WHERE image_id IS NOT NULL
    UNION ALL
    SELECT 'BRAND', CAST(id AS CHAR(64)), image_id, 'BRAND_LOGO'
    FROM brands WHERE image_id IS NOT NULL
    UNION ALL
    SELECT 'PRODUCT', CAST(product_id AS CHAR(64)), media_id, 'PRODUCT_IMAGE'
    FROM product_images
), owner_counts AS (
    SELECT media_id, COUNT(DISTINCT CONCAT(owner_type, ':', owner_id)) AS owners
    FROM refs GROUP BY media_id
)
SELECT 'MULTIPLE_AGGREGATES' AS issue, r.media_id, r.owner_type, r.owner_id
FROM refs r JOIN owner_counts c ON c.media_id = r.media_id WHERE c.owners > 1
UNION ALL
SELECT 'DANGLING_MEDIA_REFERENCE', r.media_id, r.owner_type, r.owner_id
FROM refs r LEFT JOIN media m ON m.id = r.media_id WHERE m.id IS NULL
UNION ALL
SELECT 'RELATION_PURPOSE_OR_STATUS_MISMATCH', r.media_id, r.owner_type, r.owner_id
FROM refs r JOIN media m ON m.id = r.media_id
WHERE m.purpose <> r.expected_purpose OR m.upload_status <> 'UPLOADED'
   OR m.attachment_status <> 'ACTIVE'
UNION ALL
SELECT 'ACTIVE_WITHOUT_RELATION', m.id, NULL, NULL
FROM media m LEFT JOIN refs r ON r.media_id = m.id
WHERE m.attachment_status = 'ACTIVE' AND r.media_id IS NULL;

SELECT pi.id AS product_image_id, pi.product_id, pi.media_id
FROM product_images pi LEFT JOIN products p ON p.id = pi.product_id
WHERE p.id IS NULL;
```

After changeset 014, inspect owner state and compare owner columns to the authoritative relation. Do not infer or
backfill ownership from status alone:

```sql
SELECT id, purpose, attachment_status, attachment_owner_type, attachment_owner_id
FROM media
WHERE (attachment_status = 'ACTIVE'
       AND (attachment_owner_type IS NULL OR attachment_owner_id IS NULL))
   OR (attachment_owner_type IS NULL AND attachment_owner_id IS NOT NULL)
   OR (attachment_owner_type IS NOT NULL AND attachment_owner_id IS NULL);

WITH refs AS (
    SELECT 'CATEGORY' AS owner_type, CAST(id AS CHAR(64)) AS owner_id, image_id AS media_id
    FROM categories WHERE image_id IS NOT NULL
    UNION ALL
    SELECT 'BRAND', CAST(id AS CHAR(64)), image_id FROM brands WHERE image_id IS NOT NULL
    UNION ALL
    SELECT 'PRODUCT', CAST(product_id AS CHAR(64)), media_id FROM product_images
)
SELECT r.*, m.attachment_owner_type, m.attachment_owner_id
FROM refs r JOIN media m ON m.id = r.media_id
WHERE NOT (m.attachment_owner_type <=> r.owner_type)
   OR NOT (m.attachment_owner_id <=> r.owner_id);
```

Resolve reported rows only through a separately reviewed repair decision. Until then, attachment mutation, retain,
replace, clear and delete operations for an unowned legacy ACTIVE row intentionally fail closed. Public reads and
non-attachment edits remain available. Never enable cleanup as a substitute for resolving an ACTIVE row.

## Verify FK and cleanup configuration

On MySQL 8.4, inspect actual FK definitions after migration:

```sql
SELECT rc.TABLE_NAME, rc.CONSTRAINT_NAME, rc.DELETE_RULE, rc.UPDATE_RULE,
       kcu.COLUMN_NAME, kcu.REFERENCED_TABLE_NAME, kcu.REFERENCED_COLUMN_NAME
FROM information_schema.REFERENTIAL_CONSTRAINTS rc
JOIN information_schema.KEY_COLUMN_USAGE kcu
  ON kcu.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
 AND kcu.TABLE_NAME = rc.TABLE_NAME AND kcu.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
  AND rc.CONSTRAINT_NAME IN ('fk_category_media', 'fk_brand_media', 'fk_product_image_media');
```

`fk_category_media` and `fk_brand_media` must be `RESTRICT`; `fk_product_image_media` was already `RESTRICT`.
Cleanup remains disabled by default. To opt in for one approved instance, set `MEDIA_CLEANUP_ENABLED=true` after
the migration, audit and Cloudinary adapter smoke checks. Defaults are a five-minute tick, 24-hour temporary TTL,
batch size 10, 30 provider requests per run, 15-minute transient retry and 24-hour signed-tombstone reinspection.
When disabled, cancellation still claims a tombstone, but physical cleanup does not run and backlog grows. Monitor
that explicitly; do not enable the old cleanup path as fallback.

## Cleanup behavior and operator review

- TTL eligibility uses provider `uploaded_at`; legacy rows fall back to `created_at`.
- Provider operations run outside DB transactions. Every mutation is rechecked under a short row lock.
- Scope exact-public-ID inspection by Media origin: legacy Media is only `resource_type=image`, delivery type
  `upload`; inspect `video/upload` and `raw/upload` only for signed Media with a backend-issued identity. Delete
  only confirmed Cloudinary asset IDs within that scope. Provider error, malformed response, incomplete required
  inspection or HTTP 429 is not evidence of absence; the row remains and receives a retry time.
- ACTIVE rows are never cleanup candidates. Category/Brand FKs and Product image relations prevent deleting a
  still-referenced row.
- Signed `PENDING_DELETE` rows are permanent tombstones in PR E. Even if all resources are absent or exact deletion
  succeeds, retain the row and re-inspect after 24 hours. A later upload/replay at the reserved public ID is then
  found and deleted by immutable asset ID. No automatic tombstone purge is implemented.
- Legacy `PENDING_DELETE` rows may be removed only after every exact namespace inspection is conclusive and any
  discovered exact assets are confirmed deleted/absent.

If cleanup repeatedly retries, inspect the sanitized application log by `mediaId`, then verify provider availability,
rate limits and exact resource identity. Never clear `cleanup_next_attempt_at` or delete a tombstone merely to force
another attempt. Keep database/provider identities out of public logs and do not export account inventory to Git.

## Read-only Cloudinary inventory for assets without a DB row

This is a bounded, manual report—not an automatic account-wide orphan sweeper. Use Cloudinary Admin API resource
listing separately for each resource type (`image`, `video`, `raw`) and each reserved direct-upload prefix:
`category/`, `brand/`, `product/`, and `product-description/`. Begin with `max_results=100`; persist each returned
`next_cursor` outside Git and continue from that cursor. On HTTP 429, stop and resume later from the last saved cursor.
Do not repeatedly restart from page one.

Keep only a report with exact `public_id`, `asset_id`, `resource_type`, delivery `type`, `created_at`, and whether an
exact DB row exists. Classify:

| DB match | Action |
| --- | --- |
| ACTIVE or TEMPORARY | Do not delete; investigate relation/lifecycle and use the application path. |
| PENDING_DELETE tombstone | Leave to the bounded cleanup worker; for signed rows, retain the tombstone. |
| No row | Treat as a suspected orphan only. Prefix/context resemblance is not deletion authority. |

Before manually deleting a no-row asset, separately review the reserved identity/history and authorization window,
then delete by exact `asset_id` and record the evidence. A later in-flight or replayed upload may still recreate an
asset after that final inspection, so inventory is supplemental recovery—not a guarantee of zero orphans. Legacy
proxy compensation can also create assets outside the direct prefixes; this inventory does not claim to cover them.

## Rich-text media reference contract (future integration)

When a rich-text format is approved, canonical sanitized content must contain stable `mediaId` references. The
backend must extract references from that canonical content, not trust an independent list supplied by the client.
Deduplicate references by Media ID; require `PRODUCT_DESCRIPTION_IMAGE`; synchronize its set in the same transaction
as the Product description. Gallery and description scopes are independent. Editor removal/undo/cancel must not
call Media delete; removing a persisted reference detaches it into `PENDING_DELETE`, and a detached Media cannot be
reattached. This PR does not choose HTML versus JSON, implement parsing/sanitization, or claim end-to-end
description-image synchronization.

## PR F rollout gates and observation

Direct upload is preferred for new clients; legacy multipart remains supported and is not deprecated. Code completion does not enable either worker. Reconciliation and cleanup remain disabled by default; enable only on one identified worker after owner audit/repair review and provider inspection/deletion smoke.

Before consumer rollout, save read-only legacy owner audit evidence outside Git. Resolve owner violations through a separately reviewed mapping; do not infer an owner from `ACTIVE` and do not enable cleanup as a substitute. Record the FE origin, environment, migration state, Cloudinary account/webhook configuration, and browser smoke evidence.

Observe persisted lifecycle counts from the database and provider inventory findings separately. Suggested DB snapshots:

```sql
SELECT upload_status, processing_status, attachment_status, COUNT(*) AS media_count
FROM media
GROUP BY upload_status, processing_status, attachment_status
ORDER BY upload_status, processing_status, attachment_status;

SELECT id, purpose, upload_status, processing_status, attachment_status,
       processing_reconciliation_attempts, reconciliation_next_attempt_at, processing_failure_reason,
       cleanup_next_attempt_at, upload_failure_reason, updated_at
FROM media
WHERE upload_status = 'PENDING_UPLOAD'
   OR processing_status = 'PROCESSING'
   OR attachment_status IN ('TEMPORARY', 'PENDING_DELETE')
ORDER BY updated_at ASC;
```

Confirm exact column names against changesets `011–014` before adapting queries to a deployed database. These are persisted DB counts, not proof of provider orphan state. Provider inventory prefix matches are suspected orphans only and do not authorize deletion. Track signed `PENDING_DELETE` tombstones separately: this release re-inspects and retains them; no automatic purge is provided.

For controlled activation, name the worker instance/operator, enable one scheduler at a time, and record backlog before/after, provider errors, request budget, and rollback action. Disable that worker for investigation; do not claim rollout verified without this evidence.
