# Test và vận hành Media

Luồng API, lifecycle và cách retry nằm trong [hướng dẫn sử dụng](usage.md).

## Test webhook local

1. Chạy backend ở port 8080.
2. Nếu đã cài `cloudflared`, mở terminal khác:

   ```powershell
   cloudflared tunnel --url http://localhost:8080
   ```

3. Đặt `CLOUDINARY_EAGER_NOTIFICATION_URL=https://<tunnel-host>/webhooks/cloudinary/eager` trong `.env`, restart backend.
   URL tunnel thay đổi thì cập nhật lại; môi trường deploy dùng HTTPS URL ổn định.
4. Upload ảnh và theo dõi callback. Endpoint public để Cloudinary gọi nhưng vẫn verify chữ ký trên raw body:
   `X-Cld-Signature` và `X-Cld-Timestamp`. Copy riêng body sang Postman sẽ thiếu authentication này.

| Webhook HTTP | Ý nghĩa |
| --- | --- |
| `200` | Đã nhận; có thể applied, duplicate, conflict hoặc unknown public ID bị ignore |
| `400` | Payload không hợp lệ |
| `401` | Sai/thiếu signature hoặc timestamp |
| `503` | DB/lock lỗi transient, hoặc legacy batch-only callback còn trong race window 10 phút |

Replay cần giữ nguyên bytes body và header gốc, timestamp còn hợp lệ. Duplicate được trả 200 là bình thường.
Webhook chỉ cập nhật processing; không thay thế upload verification. Terminal đáng tin cậy đầu tiên được giữ.

## Worker và cleanup

Cả hai worker mặc định **tắt**. Chỉ bật trên một instance sau migration, owner audit và provider smoke.

| Cấu hình / policy mặc định | Giá trị |
| --- | --- |
| `MEDIA_RECONCILIATION_ENABLED` | `false`; bật để recovery missing complete/callback |
| Reconciliation | Tick 1 phút, tối đa 20 provider requests/tick |
| Processing recovery | Sau 10 phút; backoff tối đa 24 giờ, dừng automatic inspection sau 7 ngày |
| `MEDIA_CLEANUP_ENABLED` | `false`; bật để xử lý expired temporary và pending deletion |
| Cleanup | Tick 5 phút, batch 10, tối đa 30 requests/tick, retry 15 phút |
| Temporary TTL | 24 giờ từ `uploaded_at`, fallback `created_at` cho legacy |
| Signed deletion | Chờ signature expiry + replay grace 10 phút; giữ tombstone, re-inspect mỗi 24 giờ |

- Thiếu variants, timeout hoặc provider outage không chứng minh FAILED; giữ PROCESSING và original fallback.
  Sau recovery horizon, webhook terminal hợp lệ đến muộn vẫn được xử lý.
- ACTIVE không cleanup. Detach chuyển PENDING_DELETE và giữ owner; không transfer/reattach.
- Legacy chỉ inspect/delete `image/upload`. Signed identities kiểm tra `image/upload`, `video/upload`, `raw/upload`.
  Chỉ xóa asset ID đã xác minh đúng scope.
- Provider error/inconclusive giữ row để retry; 429 dừng provider calls còn lại trong tick.
- Legacy row được bỏ sau scoped cleanup thành công. Signed row **không tự purge**, kể cả khi asset đã xóa.
  `overwrite=false` không chặn recreate sau deletion; replay grace không bảo đảm mọi upload in-flight đã kết thúc.
- No-row inventory chỉ report/manual recovery. Scan từng resource type và direct prefix với pagination;
  lưu cursor để tiếp tục, dừng khi 429. Prefix/context giống application chưa đủ quyền xóa.
  Asset có thể xuất hiện sau final inspection; không coi inventory là bảo đảm zero-orphan.

## Trước rollout

- [ ] Kiểm tra migrations 011–014 và Media FK Category/Brand/ProductImage đều RESTRICT.
- [ ] Chạy owner audit bên dưới bằng tài khoản SELECT-only; lưu evidence ngoài Git, chạy lại khi dừng writes.
      Resolve vi phạm bằng mapping được review riêng. Không backfill/reset DB hoặc lazy-bind owner.
- [ ] Smoke browser: signed fields giữ nguyên, không backend JWT tới Cloudinary, không tự đặt multipart Content-Type;
      kiểm tra CORS, complete, original fallback và variants.
- [ ] Test normal/early/duplicate webhook, invalid signature và cross-uploader 404.
- [ ] Attach khi UPLOADED + PROCESSING; replace/detach giữ owner và không hồi sinh PENDING_DELETE.
- [ ] Test cancel rồi replay, wrong resource type, provider absent/error và upload hoàn tất sát/sau expiry.
      Xác minh signed tombstone vẫn giữ và asset xuất hiện muộn được re-inspect.
- [ ] Chuyển một FE consumer, bật từng worker trên một instance; ghi backlog/errors/budget trước và sau.

Code complete khác rollout verified. Rich-text synchronization, automatic tombstone purge và no-row orphan deletion
chưa được triển khai. Không ghi secret, signed fields hoặc account inventory vào Git.

## Khi gặp lỗi

| Hiện tượng | Kiểm tra |
| --- | --- |
| Webhook 401 | Raw body, hai signature headers, timestamp; không dùng JWT thay chữ ký |
| Pending upload kéo dài | Complete đã gọi chưa, provider có asset không, reconciliation có bật không |
| PROCESSING kéo dài | Callback, expected derived variants, retry schedule, recovery horizon |
| Attach legacy bị từ chối | ACTIVE thiếu/sai owner; audit trước, không bật cleanup để sửa ownership |
| Cleanup backlog tăng | Worker có bật không, next attempt, quota/provider errors; signed tombstone được giữ có chủ đích |

Rollback consumer về multipart vẫn được hỗ trợ, endpoint chưa deprecated. Có thể tắt worker để điều tra;
giữ schema/data và tombstone. Không giả định có feature flag tắt direct endpoints.


<details>
<summary>SQL audit owner và FK — mở khi chuẩn bị rollout</summary>

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

</details>
