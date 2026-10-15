# Media Signed Direct Upload — Architecture Decision Record

> **Trạng thái:** Accepted for implementation planning
> **Ngày:** 14/10/2026
> **Baseline:** branch `develop`, commit `217e3704ad238fc4c6a5a1567b14403572e07692`
> **Phạm vi:** D06–D10 và D13; Modular Monolith, Cloudinary, M1.
> **Không thuộc ADR:** production code, database migration thực tế, FE implementation.

## 1. Context

Flow hiện tại nhận multipart tại Spring Boot, upload bằng Cloudinary backend SDK rồi tạo `Media`. Target flow
cho phép FE gửi file bytes trực tiếp tới Cloudinary, trong khi backend vẫn kiểm soát authorization, storage
identity, upload policy, verification, processing, attachment và cleanup.

Technical spike được ghi tại
[media-signed-direct-upload-technical-spike.md](media-signed-direct-upload-technical-spike.md). Spike xác nhận
signed parameters không thể bị sửa mà vẫn dùng chữ ký cũ; eager webhook thật có `public_id`, `asset_id` và
`batch_id`; Admin API đọc lại identity cùng metadata; eager webhook mặc định thử nghiệm không có `context`.

## 2. Decision summary

| ID | Quyết định | Lý do chính |
| --- | --- | --- |
| D06 | Tạo và commit Media/upload intent trước upload | Backend sở hữu identity; webhook sớm luôn có row để correlate |
| D07 | Dùng chung bảng `media` trong M1 | Intent là giai đoạn đầu của Media; tránh transfer giữa hai aggregate/table |
| D08 | Cho attach khi upload đã verify, không phụ thuộc processing terminal | Original asset dùng làm fallback; processing failure không làm original mất hiệu lực |
| D09 | Correlate webhook bằng `public_id`; bind thêm `asset_id`; `batch_id` phụ trợ | `public_id` biết trước upload; `asset_id` bất biến; batch chưa biết lúc tạo intent |
| D10 | `/complete` inspect asset bằng Cloudinary Admin API | Không tin URL hoặc metadata từ FE; upload response signature không bảo vệ toàn bộ metadata |
| D13 | Một Media có một owner và một usage scope | Giữ lifecycle/delete đơn giản; gallery và description dùng Media riêng |

## 3. Decision details và trade-offs

### 3.1. D06 — record trước upload

`POST /media/upload-intents` tạo row với application `mediaId`, deterministic `public_id`, uploader, purpose,
expected metadata và expiry. Transaction phải commit trước khi response chứa signed instructions được trả về.

Lợi ích:

- authorization và quota gắn với một identity đã tồn tại;
- webhook đến trước `/complete` vẫn lookup được row;
- complete/delete/status đều idempotent theo `mediaId`;
- cleanup biết chính xác prefix/key cần kiểm tra;
- FE không thể tạo Media bằng URL tùy ý.

Chi phí là có row không bao giờ upload. Scheduler chịu trách nhiệm expire và xóa chúng.

### 3.2. D07 — cùng bảng `media`

Intent trở thành Media sau verification, nên dùng cùng row. Metadata chưa tồn tại tại `PENDING_UPLOAD` được
nullable; state guards xác định tổ hợp hợp lệ. `external_id` tiếp tục non-null vì nó chứa `public_id` do backend
sinh trước upload.

Phương án bảng `media_upload_intent` riêng bị loại trong M1 vì cần chuyển/bind data giữa hai row, webhook phải
tìm ở hai nơi và complete phải giải quyết race khi tạo Media. Phương án riêng chỉ nên xem lại nếu retention,
volume hoặc audit của abandoned intents khác biệt lớn; hiện chưa có bằng chứng đó.

Trade-off của bảng chung là entity có một số field nullable. Điều này được giới hạn bằng upload state, domain
guards và migration tests, thay vì dùng placeholder URL/content type.

### 3.3. D08 — attach độc lập processing

Attach chỉ được phép khi upload là `UPLOADED`. Processing có thể `PROCESSING`, `READY` hoặc `FAILED`.
`PENDING` không hợp lệ với `UPLOADED` sau khi complete transaction kết thúc.

- `READY`: trả original và variant URLs.
- `PROCESSING`: trả original URL; variants chưa sẵn sàng.
- `FAILED`: vẫn trả original URL và failure state; admin có thể thay ảnh nếu variant là yêu cầu UX.

Điều này giữ processing và attachment độc lập. Không attach nếu provider verification chưa thành công, kể cả
khi webhook đã báo `READY`.

### 3.4. D09 — correlation identity

Khóa lookup chính là `(resource_type=image, delivery_type=upload, public_id)`; trong database hiện chỉ cần
unique `external_id` vì module chỉ quản lý Cloudinary image upload trong M1.

- `public_id`: backend sinh và lưu trước upload; webhook thật có field này.
- `asset_id`: immutable Cloudinary identity; webhook hoặc complete được phép bind lần đầu, mọi lần sau phải
  khớp.
- `batch_id`: lưu trong `processing_reference` để trace/deduplicate; không bắt buộc có trước complete.
- signed context `media_id` và `purpose`: verify qua Admin API; không dùng làm webhook lookup mặc định.

### 3.5. D10 — provider verification

`/complete` chỉ nhận `mediaId`. Backend không nhận URL, bytes, dimensions, format, `asset_id` hoặc `batch_id`
từ FE làm nguồn sự thật.

Backend gọi Admin API resource details bằng stored `public_id` và kiểm tra:

- `public_id`, `resource_type=image`, delivery `type=upload`;
- `asset_id` khớp binding nếu đã được webhook ghi;
- signed context `media_id` và `purpose`;
- actual format nằm trong allowlist;
- bytes, width và height thỏa policy của purpose;
- provider `created_at` không nằm sau upload expiry, có clock-skew tolerance;
- URL được lấy từ provider hoặc URL generator phía backend.

Admin API là rate-limited. Complete dùng lookup theo một resource, không search/list; reconciliation chạy batch
nhỏ và có backoff. Xem [Admin API usage limits](https://cloudinary.com/documentation/admin_api#usage_limits).

Cloudinary resource response chuẩn có `format`, không có canonical original MIME phù hợp để tin tuyệt đối.
M1 dùng `resource_type=image`, format allowlist và metadata thực tế. Browser `Content-Type` chỉ là expected hint.

## 4. State model

### 4.1. Persisted states

| Dimension | States | Ý nghĩa |
| --- | --- | --- |
| Upload | `PENDING_UPLOAD`, `UPLOADED` | Asset chưa hoặc đã được backend verify |
| Processing | `PENDING`, `PROCESSING`, `READY`, `FAILED` | Kết quả eager transformations |
| Attachment/lifecycle | `TEMPORARY`, `ACTIVE`, `PENDING_DELETE` | Khả năng được attach và quyền sở hữu cleanup |

Expiry/rejection/cancel không cần mở rộng upload enum. Chúng claim lifecycle thành `PENDING_DELETE` và ghi
bounded `upload_failure_reason` khi cần. Một intent pending quá `upload_expires_at` được coi là expired trước
khi scheduler claim nó.

`PENDING_DELETE` không có nghĩa Media đã từng được attach. Đây là cleanup claim dùng chung: row không còn
được phép attach, một cleanup worker được quyền kiểm tra/xóa provider asset, và tombstone phải được giữ đến
khi cửa sổ replay đã đóng. Vì vậy intent chưa upload, asset upload sai policy và Media vừa detach đều có thể
đi thẳng từ `TEMPORARY` hoặc `ACTIVE` sang `PENDING_DELETE` mà không thay đổi upload/processing state. Không
thêm enum mới chỉ để biểu diễn lý do cleanup; expiry và failure reason cung cấp đủ ngữ cảnh trong M1.

### 4.2. Valid combinations

| Upload | Processing | Attachment | Hợp lệ khi |
| --- | --- | --- | --- |
| `PENDING_UPLOAD` | `PENDING` | `TEMPORARY` | Intent mới, chưa có callback |
| `PENDING_UPLOAD` | `READY` hoặc `FAILED` | `TEMPORARY` | Webhook đến trước complete |
| `PENDING_UPLOAD` | bất kỳ | `PENDING_DELETE` | Canceled, expired hoặc provider validation bị reject |
| `UPLOADED` | `PROCESSING`, `READY` hoặc `FAILED` | `TEMPORARY` | Verified nhưng chưa attach |
| `UPLOADED` | `PROCESSING`, `READY` hoặc `FAILED` | `ACTIVE` | Đang được một owner sử dụng |
| `UPLOADED` | `PROCESSING`, `READY` hoặc `FAILED` | `PENDING_DELETE` | Đã detach/delete và chờ physical cleanup |

Các tổ hợp `PENDING_UPLOAD + ACTIVE` và `UPLOADED + PENDING` không hợp lệ sau commit.

### 4.3. Transition table

| Event | Preconditions | Locked update | Result |
| --- | --- | --- | --- |
| Create intent | Caller/purpose/policy hợp lệ | Insert row | `PENDING_UPLOAD/PENDING/TEMPORARY` |
| Webhook success sớm | Valid signature; matching `public_id`; row không deleting | Bind/check `asset_id`, `batch_id` | `PENDING_UPLOAD/READY/TEMPORARY` |
| Webhook failure sớm | Như trên | Bind/check identity, store bounded reason | `PENDING_UPLOAD/FAILED/TEMPORARY` |
| Complete bình thường | Caller owns pending intent; provider asset valid | Bind/check identity, metadata, URL | `UPLOADED/PROCESSING/TEMPORARY` |
| Complete sau terminal webhook | Như trên | Upload thành `UPLOADED`, giữ processing | Upload `UPLOADED`; processing `READY` hoặc `FAILED`; attachment `TEMPORARY` |
| Complete transient failure | Provider timeout/429/5xx | Không mutate business state | Giữ state; trả retryable error |
| Complete permanent validation failure | Provider response authoritative nhưng asset sai policy | Store safe reason; claim deletion | `PENDING_UPLOAD/*/PENDING_DELETE` |
| Attach | Đủ toàn bộ attachment eligibility ở mục 4.4 | Set owner và activate trong cùng transaction nghiệp vụ | `UPLOADED/*/ACTIVE` |
| Detach/delete | Owner hoặc uploader được phép | Claim deletion | `*/ */PENDING_DELETE` |
| Cleanup success | `PENDING_DELETE`, replay window đã đóng nếu là intent | Delete provider asset rồi delete row | Row biến mất |

`*` trong attachment transitions chỉ gồm processing states được liệt kê là hợp lệ.

### 4.4. Attachment eligibility

Một Media chỉ được attach khi đồng thời thỏa mãn:

- upload đã được backend verify và đang là `UPLOADED`;
- lifecycle đang là `TEMPORARY`, chưa bị cleanup claim;
- caller là uploader hoặc có quyền ứng dụng rõ ràng để sử dụng Media;
- requested owner type, owner ID và `MediaPurpose` đúng với use case;
- Media chưa được attach vào owner hoặc usage scope khác.

Processing không phải điều kiện attach: `PROCESSING`, `READY` và `FAILED` đều hợp lệ, còn `PENDING` không thể
đi cùng `UPLOADED` sau commit. Trong PR B, repository guard bổ sung `upload_status=UPLOADED` vào điều kiện
attach hiện tại. Owner identity đầy đủ và invariant one-owner/one-scope được bổ sung ở PR E; cho đến lúc đó,
row lock, `TEMPORARY` và quan hệ Catalog hiện tại tiếp tục ngăn hai attach cạnh tranh trong cùng database.

## 5. Webhook-before-complete và concurrency

### 5.1. Webhook được phép cập nhật sớm

Webhook đã qua signature verification được phép bind `asset_id`, `batch_id` và chuyển processing
`PENDING -> READY|FAILED` trong khi upload vẫn `PENDING_UPLOAD`. Nó không được chuyển upload sang `UPLOADED`
vì chưa chạy business verification.

Nếu webhook báo `READY` nhưng complete:

- gặp lỗi provider tạm thời: giữ `READY`, giữ `PENDING_UPLOAD`, trả retryable error;
- xác minh asset hợp lệ ở lần retry/reconciliation: chuyển upload thành `UPLOADED`, giữ `READY`;
- xác minh asset sai policy: không attach; claim `PENDING_DELETE`, giữ processing result để audit cho tới cleanup.

### 5.2. Không giữ transaction khi gọi provider

Complete có ba bước:

1. Transaction ngắn đọc intent, kiểm tra caller/state và lấy verification snapshot.
2. Ngoài transaction, gọi Cloudinary Admin API.
3. Transaction ngắn `SELECT ... FOR UPDATE`, kiểm tra lại state/identity rồi merge kết quả.

Webhook dùng `SELECT ... FOR UPDATE` theo `public_id`. Vì complete finalize và webhook serialize trên cùng row,
complete chỉ chuyển `PENDING -> PROCESSING`; nó không ghi đè `READY/FAILED` đã commit.

Hai complete concurrent có thể cùng thực hiện provider GET. Đây là duplicate read chấp nhận được trong M1.
Khi finalize, request thứ hai thấy `UPLOADED`: nếu cùng `asset_id` thì trả cùng response; mismatch trả conflict.
Không thêm distributed lock hoặc lease state chỉ để tránh một provider read hiếm gặp.

### 5.3. Duplicate, conflict và unknown webhook

- Same identity + same terminal outcome: `200`, no-op.
- Same identity + conflicting terminal outcome: `200`, giữ first terminal outcome, log và enqueue/mark để
  reconciliation kiểm tra.
- Stored `asset_id` khác incoming: không mutate, log security/integrity event, trả `200` để tránh retry vô hạn.
- Không có row theo `public_id`: log và trả `200`. Intent đã commit trước khi signature được phát hành, nên
  provider retry không thể sửa missing application identity; transient database errors vẫn trả `503`.
- Parse payload theo kiểu forward-compatible, bỏ qua unknown fields.

Cloudinary ký raw webhook và retry ba lần khi response khác `200`; retry diễn ra sau khoảng 3, 6 và 9 phút.
Xem [Webhook retry policy](https://cloudinary.com/documentation/notifications#retry_policy).

## 6. Signed upload replay và overwrite

### 6.1. Required signed policy

Backend ký tối thiểu:

- deterministic `public_id`;
- `overwrite=false`;
- eager transformations, `eager_async=true`, `eager_notification_url`;
- context gồm `media_id` và `purpose`;
- allowed formats và các provider policy dùng trong M1;
- timestamp.

FE chỉ được thêm `file`. Backend trả cố định endpoint `image/upload`; API secret không rời backend.

Cloudinary signed upload mặc định cho phép overwrite, nên `overwrite=false` phải được ký rõ ràng. Với
deterministic `public_id`, Cloudinary bỏ qua upload mới nếu asset đã tồn tại thay vì thay file cũ. Xem
[Upload API overwrite](https://cloudinary.com/documentation/image_upload_api_reference#upload) và
[avoiding duplicate uploads](https://cloudinary.com/documentation/upload_images#avoiding_duplicate_uploads).

Hệ quả là request upload hợp lệ đầu tiên thắng. Nếu file đầu tiên không qua business validation, intent bị
reject và cleanup; client phải xin intent mới với `public_id` mới, không được overwrite intent cũ.

### 6.2. Replay window

Cloudinary upload signature có hiệu lực một giờ từ signed timestamp. Application không thể thu hồi chữ ký đã
phát hành. Xem
[authentication signature considerations](https://cloudinary.com/documentation/authentication_signatures#important_considerations_for_authentication_signatures).

Policy:

- `upload_expires_at = signed timestamp + 1 hour`; response FE dùng cùng expiry, có thể trừ một safety margin
  nhỏ cho UI nhưng cleanup vẫn theo provider expiry;
- late complete được chấp nhận sau expiry trong completion grace nếu provider `created_at <= upload_expires_at`
  và row chưa bị claim delete;
- cancel/expire chuyển row sang `PENDING_DELETE`, nhưng giữ tombstone và tiếp tục kiểm tra provider cho tới
  `upload_expires_at + replay_grace`;
- không xóa row ngay khi provider báo `not found` nếu chữ ký vẫn còn hiệu lực;
- `overwrite=false` chỉ bảo vệ khi asset cùng `public_id` còn tồn tại. Nếu asset bị xóa trong lúc signature
  còn hiệu lực, request cũ có thể tạo lại asset;
- cleanup có thể xóa asset sớm để giảm exposure/cost, nhưng không được xóa tombstone. Sau replay window,
  worker phải inspect lại đúng `public_id`, xóa idempotently nếu asset đã được tạo lại, rồi mới xóa row;
- nếu final inspect/delete thất bại, giữ tombstone `PENDING_DELETE` và retry; provider `not found` chỉ được coi
  là kết quả cuối cùng khi replay window đã đóng;
- uploaded temporary Media có TTL 24 giờ tính từ `uploaded_at`, nên replay window đã đóng trước cleanup thông
  thường.

Như vậy orphan do replay được phát hiện bằng final provider inspection theo `public_id` trong khi tombstone
vẫn giữ identity. Không cần thêm `DELETED` state hoặc bảng orphan riêng trong M1.

ADR chưa chốt giá trị `replay_grace`; đề xuất 10 phút để hấp thụ clock skew và scheduler delay. D11 sẽ chốt
giá trị cấu hình.

## 7. Cloudinary Admin API failure policy

### 7.1. Complete request

| Provider result | API behavior | State mutation |
| --- | --- | --- |
| Valid resource | `200` với Media status | Finalize `UPLOADED` idempotently |
| Definitive policy mismatch | `422` | Claim `PENDING_DELETE`, safe failure reason |
| Resource not found trước expiry/grace | `409` upload not visible/completed | Không mutate; FE có thể retry |
| Timeout, connection error, `429`, provider `5xx` | `503`, optional `Retry-After` | Không mutate |

Adapter được phép retry tối đa một lần cho idempotent GET với timeout ngắn và jitter. Với `429`, ưu tiên
`Retry-After`; không loop trong request. Không thêm resilience dependency trong M1.

### 7.2. Reconciliation

Reconciliation là bắt buộc cho hai nhóm:

1. `PENDING_UPLOAD` đã qua upload expiry nhưng chưa complete:
   - provider không có asset: giữ tombstone tới hết replay grace rồi xóa row;
   - có asset hợp lệ được tạo trước expiry: chạy cùng verification/finalization service như complete;
   - asset sai policy: claim/delete.
2. `PROCESSING` quá timeout:
   - inspect expected derived transformations theo batch nhỏ;
   - đủ variants: `READY`;
   - chưa đủ: retry có backoff;
   - quá attempt/age limit: `FAILED` với safe reason.

Scheduler không giữ DB lock trong provider call. Nó snapshot candidates, gọi provider, rồi conditional
update/row lock để finalize. Batch size, timeout và retry count thuộc D12/configuration.

## 8. API contract proposal

Tên field là contract đề xuất cho implementation plan; DTO Java chưa được chốt.

### 8.1. Create upload intent

```http
POST /media/upload-intents
Authorization: Bearer <ADMIN JWT>
Content-Type: application/json
```

```json
{
  "purpose": "PRODUCT_IMAGE",
  "filename": "front.png",
  "contentType": "image/png",
  "sizeBytes": 182340
}
```

Response `201`:

```json
{
  "mediaId": "uuid",
  "expiresAt": "2026-10-14T11:00:00Z",
  "upload": {
    "provider": "CLOUDINARY",
    "method": "POST",
    "url": "https://api.cloudinary.com/v1_1/<cloud>/image/upload",
    "fields": {
      "api_key": "public-key",
      "timestamp": "...",
      "signature": "...",
      "public_id": "media/product-image/<mediaId>",
      "overwrite": "false",
      "eager_async": "true"
    }
  },
  "status": {
    "upload": "PENDING_UPLOAD",
    "processing": "PENDING",
    "attachment": "TEMPORARY"
  }
}
```

Backend ký thêm policy fields dù ví dụ rút gọn không hiển thị hết. `fields` là map string phục vụ multipart
Cloudinary, không phải abstraction chung cho mọi provider.

### 8.2. Complete

```http
POST /media/{mediaId}/complete
Authorization: Bearer <ADMIN JWT>
```

Không có request body. Response `200` cho cả lần đầu và retry thành công:

```json
{
  "mediaId": "uuid",
  "purpose": "PRODUCT_IMAGE",
  "uploadStatus": "UPLOADED",
  "processingStatus": "PROCESSING",
  "attachmentStatus": "TEMPORARY",
  "originalUrl": "https://...",
  "variants": {}
}
```

### 8.3. Get status

```http
GET /media/{mediaId}
Authorization: Bearer <ADMIN JWT>
```

Response `200` dùng cùng Media status representation. `variants` chỉ chứa URL sẵn sàng; `originalUrl` chỉ
có sau verification. FE poll ngắn khi processing `PROCESSING`; không thêm SSE/WebSocket trong M1.

### 8.4. Delete/cancel

```http
DELETE /media/{mediaId}
Authorization: Bearer <ADMIN JWT>
```

- Uploader có thể cancel pending hoặc delete temporary Media.
- Retry khi đã `PENDING_DELETE` trả `204`.
- Media `ACTIVE` không xóa trực tiếp qua endpoint này; trả `409`.
- Physical delete chạy async; response `204` chỉ xác nhận delete đã được claim.

Để tránh lộ media của user khác, lookup không thuộc uploader trả cùng `404` như media không tồn tại.

## 9. Database and migration design

Thêm forward Liquibase changeset mới; không sửa các baseline changeset đã áp dụng.

### 9.1. Media upload fields

| Column | Type/constraint dự kiến | Migration/backfill |
| --- | --- | --- |
| `upload_status` | `VARCHAR(32) NOT NULL` | Existing rows = `UPLOADED` |
| `asset_id` | `VARCHAR(255) NULL UNIQUE` | Null cho legacy; bind khi Cloudinary inspect/webhook cung cấp |
| `upload_expires_at` | `TIMESTAMP NULL` | Null cho legacy uploaded rows |
| `uploaded_at` | `TIMESTAMP NULL` | Existing rows lấy `created_at` |
| `expected_content_type` | `VARCHAR(100) NULL` | Client hint; không dùng làm actual truth |
| `expected_size_bytes` | `BIGINT NULL` | Client hint/prevalidation |
| `bytes` | `BIGINT NULL` | Provider metadata sau verify |
| `width` / `height` | `INT NULL` | Provider metadata sau verify |
| `format` | `VARCHAR(32) NULL` | Provider format sau verify |
| `upload_failure_reason` | `VARCHAR(500) NULL` | Safe bounded reason |

Thay đổi existing columns:

- `url` nullable chỉ khi `upload_status=PENDING_UPLOAD` hoặc row đang delete;
- `content_type` nullable cho pending intent; sau verify lưu normalized type suy ra từ provider format;
- `external_id` giữ non-null/unique và chứa backend-issued `public_id`;
- `processing_status` thêm `PENDING`; existing values giữ nguyên;
- `processing_reference` tiếp tục nullable/unique cho `batch_id`.

MySQL check constraints chỉ thêm khi version đang dùng thực sự enforce chúng; domain guards và integration
tests vẫn là lớp bắt buộc. Migration test phải chứng minh legacy Media vẫn đọc được và new pending row hợp lệ.

### 9.2. Attachment owner fields trong PR E

Để enforce D13 xuyên Category/Brand/Product, thêm:

| Column | Type | Ý nghĩa |
| --- | --- | --- |
| `attachment_owner_type` | `VARCHAR(32) NULL` | `CATEGORY`, `BRAND`, `PRODUCT` |
| `attachment_owner_id` | `VARCHAR(64) NULL` | Opaque aggregate ID |

`purpose` là usage scope và immutable. `ACTIVE` row mới phải có cả hai owner fields. Attach lần hai chỉ
idempotent khi cùng owner và purpose; owner khác bị conflict.

Trước khi enforce, migration audit/backfill từ `categories.image_id`, `brands.image_id` và
`product_images.media_id`. Nếu một legacy Media xuất hiện ở nhiều owner hoặc scope, migration phải report và
dừng thay vì chọn owner tùy ý. Catalog foreign keys/relations vẫn là source cho presentation/order; Media owner
fields là guard cho lifecycle. Hai phía được cập nhật trong cùng local transaction.

## 10. Cleanup policy

| Nhóm | Eligibility | Action |
| --- | --- | --- |
| Pending intent, không asset | Sau provider expiry + replay grace | Final inspect; delete row nếu vẫn absent |
| Pending intent, valid asset, no complete | Sau upload expiry | Reconciliation auto-finalize thành `UPLOADED` nếu asset được tạo đúng hạn |
| Pending intent, invalid asset | Khi complete/reconcile xác nhận mismatch | Claim `PENDING_DELETE`; có thể xóa sớm nhưng giữ tombstone và final inspect/delete sau replay window |
| Uploaded temporary | 24 giờ từ `uploaded_at`, vẫn `TEMPORARY` | Atomic claim rồi delete asset/row |
| Detached/canceled | `PENDING_DELETE` | Giữ tombstone; final inspect/delete sau replay window nếu signed request còn khả năng recreate |
| Provider delete failure | Bất kỳ | Giữ row và retry; `not found` được coi là delete success sau replay window |

Attach và cleanup dùng conditional update hoặc row lock. Cleanup chỉ thắng nếu row vẫn `TEMPORARY`; attach
đã chuyển `ACTIVE` khiến claim ảnh hưởng zero rows.

Cleanup không xóa database row chỉ vì một lần provider delete/not-found xảy ra trước
`upload_expires_at + replay_grace`. Đây là guard bắt buộc vì `overwrite=false` không ngăn recreate sau delete.

## 11. Security policy

- Giữ `ADMIN` cho intent, complete, status và delete trong M1.
- API secret không trả về FE; API key được phép nằm trong upload fields.
- Backend sinh `mediaId`, `public_id`, context, callback và eager policy.
- Ký `overwrite=false`; không dùng original filename làm public identity.
- Validate expected size/content type khi tạo intent để fail sớm, rồi validate provider metadata khi complete.
- Không nhận URL từ FE; không coi upload response metadata là authoritative.
- Webhook public ở network layer nhưng verify raw-body signature và timestamp trước parse/mutation.
- Rate limit intent creation và giới hạn open intents theo uploader; giá trị cụ thể thuộc D15/D16.
- Không log signature, API secret, raw signed fields hoặc full webhook body.
- Public ID dùng random `mediaId`, không chứa email/user data.

Rủi ro còn lại: signed file bytes không bị khóa bởi signature và signature không thể revoke trong một giờ.
Provider verification, `overwrite=false`, tombstone và cleanup sau replay window là các lớp giảm thiểu.

## 12. PR plan, acceptance criteria và rollback

### PR B — Upload lifecycle foundation

Phạm vi:

- forward migration cho upload/processing fields;
- domain transitions và persistence queries/locks;
- application command/result và internal use case tạo upload intent, chưa phát Cloudinary signature;
- adapt legacy backend upload để tạo row `UPLOADED` như trước;
- legacy cleanup không được claim nhầm `PENDING_UPLOAD`;
- chưa expose direct upload API hoặc API DTO ra HTTP; route intent đi cùng signer ở PR C.

Acceptance:

- legacy upload/catalog flows không đổi contract;
- existing rows backfill `UPLOADED` và vẫn đọc được;
- valid/invalid state transitions có representative tests;
- migration chạy trên fresh baseline và schema có dữ liệu legacy đại diện.

Rollback: application có thể quay lại pre-B vì chưa có `PENDING_UPLOAD` rows; additive columns được giữ.

### PR C — Signed intent, complete và status

Phạm vi:

- intent/complete/status/delete API contracts;
- Cloudinary signature adapter và Admin API inspector;
- idempotent complete, provider error mapping và OpenAPI tests;
- legacy multipart API vẫn tồn tại.

Acceptance:

- FE upload bytes trực tiếp bằng signed fields;
- tampered signed field bị provider reject;
- forged URL/metadata không có đường vào complete;
- retry complete cùng asset trả cùng Media;
- caller khác không complete/status/delete được intent.

Rollback: rollback về artifact PR B chỉ sau khi disable traffic tới direct endpoints. PR B phải đọc và cleanup
được pending rows; không rollback về pre-B khi database đã có pending rows.

### PR D — Webhook correlation và reconciliation

Phạm vi:

- lookup webhook theo `public_id`, bind `asset_id`/`batch_id`;
- early/duplicate/conflicting webhook handling;
- bounded reconciliation cho pending upload và stuck processing.

Acceptance:

- webhook trước/đồng thời/sau complete không mất terminal result;
- invalid signature không mutate;
- missing callback được reconciliation xử lý;
- provider outage không biến transient error thành permanent failure.

Rollback: direct upload chưa rollout rộng trước khi PR D ổn định. Rollback D về C tạm dừng direct upload và giữ
rows để xử lý sau; không xóa data.

### PR E — Attachment và cleanup hardening

Phạm vi:

- Media application attachment contract;
- opaque owner fields, audit/backfill và single-owner enforcement;
- intent/orphan/replay-aware cleanup;
- rich-text reference contract, chưa chọn HTML hay JSON.

Acceptance:

- một Media không attach sang owner/scope khác;
- duplicate reference trong cùng description được deduplicate;
- attach/cleanup race không xóa asset đang dùng;
- cancel/replay không tạo orphan tồn tại ngoài reconciliation.

Rollback: owner columns giữ additive. Nếu rollback application, dừng attachment writes mới và giữ cleanup
worker phiên bản tương thích cho tới khi pending deletions hết.

### PR F — FE handoff và rollout

Phạm vi:

- final OpenAPI/FE examples, polling/fallback behavior và runbook;
- staged rollout, metrics/logs, smoke test;
- sau khi FE chuyển ổn định, đánh dấu legacy proxy endpoint deprecated trong Java/OpenAPI nhưng tiếp tục giữ nó
  hoạt động như compatibility/reference flow;
- direct upload là flow mặc định cho client mới; legacy flow giữ authorization, validation, lifecycle và test đại
  diện hiện có, nhưng không nhận thêm capability mới;
- không xóa legacy endpoint trong CR này. Removal, nếu cần, là một CR và quyết định riêng sau khi xác nhận không
  còn consumer.

Acceptance:

- end-to-end signed direct upload + early/normal webhook smoke test;
- FE hiển thị original fallback khi processing chưa ready/failed;
- orphan, stuck processing và cleanup failures quan sát được;
- legacy catalog Media vẫn hoạt động.

Rollback: FE quay lại legacy multipart API còn được giữ; direct endpoints có thể disable ở routing/config. Không
drop columns hoặc xóa legacy endpoint trong cùng rollout.

## 13. Remaining decisions and risks

ADR chốt D06–D10 và D13. Các giá trị sau vẫn cần chốt trong implementation plan:

- D11: replay grace, complete grace và scheduler frequency;
- D12: processing timeout, reconciliation batch/attempt limits;
- D15: rate limit và open-intent quota cho ADMIN;
- D16: allowed formats, max bytes/dimensions, GIF/SVG policy;
- D17: polling interval và client timeout;
- D18 đã chốt: deprecate legacy multipart API sau rollout, tiếp tục giữ hoạt động; không disable/remove trong CR
  này;
- D19: naming chính thức của owner type và public ID prefix.

Các rủi ro cần kiểm chứng trong PR tests:

- Cloudinary folder mode khác nhau giữa environment;
- Admin API rate limit khi reconciliation backlog lớn;
- partial eager failure và cách xác định đủ expected variants;
- legacy data vi phạm single-owner trước owner backfill;
- clock skew giữa backend và provider quanh expiry.
