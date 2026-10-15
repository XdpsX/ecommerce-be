# Media Signed Direct Upload — Cloudinary Technical Spike

> **Ngày khảo sát:** 14/10/2026
> **Baseline code:** branch `develop`, commit `217e3704ad238fc4c6a5a1567b14403572e07692`
> **Phạm vi:** PR A — research/technical spike; chưa thay đổi production code hoặc database schema.

## 1. Kết luận đề xuất

Cloudinary Signed Direct Upload phù hợp với target flow của CR, với thiết kế sau:

1. Backend tạo và commit một Media/upload intent trước khi trả upload instructions.
2. Backend cấp `mediaId` và `public_id` duy nhất, ký toàn bộ upload policy cần kiểm soát.
3. FE chỉ thêm file bytes rồi upload trực tiếp tới endpoint Cloudinary do backend trả về.
4. `/complete` không tin metadata từ FE; backend đọc asset bằng Cloudinary Admin API và đối chiếu intent.
5. Eager webhook correlate chính bằng `public_id` đã lưu từ lúc tạo intent.
6. Sau khi biết, lưu thêm immutable `asset_id`; dùng `batch_id` để trace và phát hiện duplicate, không dùng
   làm khóa correlation duy nhất.
7. Giữ ba chiều lifecycle độc lập: upload, processing và attachment.
8. Trong M1, mỗi Media chỉ có một owner và một `MediaPurpose`; gallery và rich text dùng Media riêng.

`context` có thể mang `media_id` và `purpose` để backend kiểm tra asset khi complete, nhưng không nên dùng
làm correlation cho eager webhook mặc định: thử nghiệm thực tế không thấy `context` trong payload đó.

## 2. Cách phân loại bằng chứng

- **[DOC]**: được tài liệu Cloudinary chính thức xác nhận.
- **[TEST]**: được thử nghiệm thực tế trên Cloudinary product environment đang cấu hình cho local.
- **[CODE]**: được xác nhận từ code trên baseline `develop`.
- **[DESIGN]**: đề xuất thiết kế suy ra từ các bằng chứng trên; cần review trước implementation.

## 3. Baseline hiện tại

**[CODE]** Backend hiện nhận multipart, đọc kích thước bằng `ImageIO`, upload qua SDK rồi mới tạo `Media`.
`CloudinaryMediaStorage` yêu cầu `public_id`, URL và eager `batch_id` trong upload response. Handler webhook
tìm Media bằng `processingReference=batch_id`.

**[CODE]** Bảng `media` hiện yêu cầu `external_id`, `url`, `content_type`, `purpose` và attachment status.
Nó chưa biểu diễn được intent tồn tại trước asset vì `url` và metadata thực tế chưa có ở thời điểm đó.

**[CODE]** `uploadedByUserId` đã bảo vệ upload tạm khi attach/delete. Đây là uploader authorization, không
thay thế attachment owner của Product/Brand/Category.

**[CODE]** Catalog hiện truy cập `MediaRepository` trực tiếp để claim/activate Media. Các CR sau cần đưa
flow này qua Media application contract, nhưng PR A không sửa luồng đó.

## 4. Signed Direct Upload

### 4.1. Điều Cloudinary bảo đảm

**[DOC]** Upload signature được tạo từ các upload parameters, sắp xếp theo tên, cộng API secret rồi hash.
`file`, `cloud_name`, `resource_type` và `api_key` không nằm trong chuỗi ký. Chữ ký có hiệu lực một giờ từ
`timestamp`. API secret phải chỉ nằm ở backend. Xem
[Generating authentication signatures](https://cloudinary.com/documentation/authentication_signatures) và
[Cloudinary signatures](https://cloudinary.com/documentation/signatures).

**[DOC]** `public_id`, eager transformations, `eager_async`, `eager_notification_url`, `context`,
`allowed_formats`, `overwrite` và các option upload khác có thể được đưa vào signed request. Nếu FE đổi một
signed field mà không có chữ ký mới, Cloudinary từ chối request. Xem
[Upload API reference](https://cloudinary.com/documentation/image_upload_api_reference).

**[DOC]** Với signed upload preset, request parameters thường ghi đè preset; riêng eager và incoming
transformation được merge. Vì rule này làm policy khó nhìn hơn, spike đề xuất chưa dùng preset làm nguồn
policy chính trong M1. Xem [Upload preset precedence](https://cloudinary.com/documentation/upload_presets#upload_preset_precedence).

### 4.2. Upload instructions đề xuất

**[DESIGN]** Backend trả một object có cấu trúc provider-specific vừa đủ:

```json
{
  "mediaId": "application-id",
  "expiresAt": "provider-signature-expiry",
  "upload": {
    "url": "https://api.cloudinary.com/v1_1/<cloud>/image/upload",
    "method": "POST",
    "fields": {
      "api_key": "public-api-key",
      "timestamp": "...",
      "signature": "...",
      "public_id": "media/<purpose>/<mediaId>",
      "context": "media_id=<mediaId>|purpose=<purpose>",
      "eager": "<server-controlled transformations>",
      "eager_async": "true",
      "eager_notification_url": "<server-controlled callback>",
      "allowed_formats": "<purpose allowlist>",
      "overwrite": "false"
    }
  }
}
```

FE phải gửi lại đúng toàn bộ fields và chỉ thêm `file`. `public_id` phải được backend sinh duy nhất và có
unique constraint trong database. Không dùng original filename làm identity.

### 4.3. Giới hạn bảo mật

**[DOC]** File bytes không nằm trong chữ ký. Signed upload xác thực policy của request, không chứng minh file
phù hợp nghiệp vụ. Chữ ký còn có thể được replay trong cửa sổ một giờ.

**[DESIGN]** Cần ký `overwrite=false`. Với `public_id` duy nhất, replay không được thay thế asset đã upload.
Tuy nhiên, request đầu tiên vẫn có thể gửi file không đúng dự kiến; `/complete` phải verify và cleanup asset
không hợp lệ.

**[DOC]** `resource_type` nằm trong URL và không thuộc chuỗi ký. Backend phải trả endpoint cố định
`image/upload`, đồng thời `/complete` chỉ chấp nhận resource `image`, delivery type `upload` và đúng
`public_id`. Asset được tạo sai resource type không được complete và phải nằm trong reconciliation/cleanup
theo prefix intent.

**[DOC]** Upload preset không có giới hạn file size riêng; giới hạn trên Upload Widget chủ yếu là client-side.
Cloudinary có account upload limits và `allowed_formats`, nhưng backend vẫn phải kiểm tra actual bytes và
dimensions. Xem [Upload preset security](https://cloudinary.com/documentation/upload_presets#signed_and_unsigned_upload_preset_security).

**[DESIGN]** Endpoint tạo intent cần authentication, authorization theo purpose, rate limit/quota và giới hạn
số intent đang mở. Đây là lớp chống abuse chính trước khi cấp chữ ký.

## 5. Kết quả thử nghiệm thực tế

### 5.1. Thiết lập

**[TEST]** Spike dùng API key/secret và webhook.site đã có trong `.env`; không in secret ra log. Backend sinh:

- UUID cho `mediaId` và deterministic `public_id`;
- signed `public_id`, `context`, eager transformation, `eager_async=true`, callback URL và
  `overwrite=false`;
- ảnh PNG 2x2 tạm thời.

Asset test được xóa ngay sau thử nghiệm; Cloudinary trả `result=ok`.

### 5.2. Quan sát

| Kiểm tra | Kết quả |
| --- | --- |
| Đổi `public_id` nhưng giữ chữ ký | Cloudinary trả HTTP `401` |
| Request với đúng signed fields | HTTP `200` |
| Upload response | Có đúng `public_id`, `asset_id`, `resource_type=image`, `format`, `bytes`, `width`, `height` |
| Async eager response | Có `batch_id` |
| Admin API lookup theo `public_id` | HTTP `200`; identity và metadata khớp upload response |
| Context lưu trên asset | `media_id` khớp giá trị backend đã ký |
| Eager webhook | Nhận được, có signature headers |
| Correlation | `public_id` và `batch_id` trong webhook khớp upload response |

**[TEST]** Eager webhook thực tế có các top-level fields:

```text
notification_type, timestamp, request_id, batch_id, eager,
asset_id, public_id, api_key, notification_context, signature_key
```

Mỗi eager item có:

```text
transformation, width, height, bytes, format, url, secure_url
```

Payload eager thử nghiệm **không có `context`**, dù context đã được lưu trên asset và đọc lại thành công qua
Admin API. Đây là lý do không chọn context làm khóa webhook chính.

### 5.3. Phạm vi chưa được thử nghiệm

- Failure payload của eager transformation.
- Toàn bộ retry schedule trên account khi endpoint trả non-`200`.
- Upload có file lớn, GIF/SVG hoặc định dạng giả mạo.
- Callback bị mất hoàn toàn và reconciliation theo derived resources.
- Concurrent `/complete`, webhook và cleanup trong application/database.

Các hành vi này cần integration tests hoặc smoke test riêng trong PR tương ứng.

## 6. Webhook và correlation

### 6.1. Hành vi được xác nhận

**[DOC]** Với `eager_async=true`, upload response và eager notification đều có `batch_id`. Notification được
gửi sau khi eager transformations hoàn tất. Xem
[Eager transformation notifications](https://cloudinary.com/documentation/notifications#eager_transformation_notifications).

**[DOC]** Cloudinary ký raw notification body và gửi `X-Cld-Signature`, `X-Cld-Timestamp`. Endpoint phải
verify raw body trước khi parse và kiểm tra timestamp. Xem
[Verifying notification signatures](https://cloudinary.com/documentation/notification_signatures).

**[DOC]** Khi webhook không trả `200`, Cloudinary thực hiện ba lần retry sau khoảng 3, 6 và 9 phút. Retry
giúp xử lý lỗi tạm thời nhưng không bảo đảm callback sẽ đến cuối cùng. Xem
[Webhook retry policy](https://cloudinary.com/documentation/notifications#retry_policy).

### 6.2. So sánh phương án correlation

| Phương án | Ưu điểm | Nhược điểm | Kết luận |
| --- | --- | --- | --- |
| `batch_id` FE gửi khi complete | Trùng với eager webhook | Backend không biết trước upload; webhook có thể đến trước complete. Upload response signature chỉ bảo vệ `public_id` và `version`, không bảo vệ `batch_id` | Chỉ dùng phụ trợ/trace |
| Signed `context.media_id` | Được backend ký; Admin API đọc lại được | Eager webhook mặc định trong test không có context | Dùng verify asset, không dùng correlation chính |
| Backend-issued `public_id` | Biết và lưu trước upload; signed; webhook thật có field này | Phải quản lý uniqueness và folder mode rõ ràng | **Correlation chính** |
| Cloudinary `asset_id` | Immutable; webhook và Admin API đều trả | Chưa biết lúc tạo intent | Binding thứ cấp sau upload |
| `request_id` | Hữu ích cho log | Không tồn tại trước request và không đại diện Media | Chỉ dùng cho observability |

**[DESIGN]** Handler mới lookup intent bằng tổ hợp expected `resource_type=image`, `type=upload` và
`public_id`. Khi row đã có `asset_id`, webhook phải khớp cả `asset_id`; mismatch bị reject/log. `batch_id`
được lưu nếu chưa có và phải khớp ở các event sau.

Parser tiếp tục ignore unknown fields vì Cloudinary có thể bổ sung response fields trong tương lai.

## 7. Verify asset trong `/complete`

### 7.1. Nguồn dữ liệu đáng tin cậy

**[DOC]** Admin API `GET /resources/:resource_type/:type/:public_id` trả `asset_id`, `public_id`, `format`,
`resource_type`, `type`, `created_at`, `bytes`, `width`, `height`, URL, context và derived assets. API dùng
server credentials và có rate limit; free plan hiện được tài liệu ghi là 500 request/giờ. Xem
[Get details of a single resource](https://cloudinary.com/documentation/admin_api#get_details_of_a_single_resource_by_public_id)
và [Admin API usage limits](https://cloudinary.com/documentation/admin_api#usage_limits).

**[DOC]** Signature trong upload response chỉ bao phủ `public_id` và `version`, không bao phủ bytes,
dimensions, format hoặc `batch_id`. Vì vậy, gửi nguyên upload response từ FE về vẫn không đủ để tin toàn bộ
metadata. Xem [Verifying response signatures](https://cloudinary.com/documentation/response_signatures).

### 7.2. Validation đề xuất

**[DESIGN]** `/complete` không cần nhận URL hoặc provider metadata. Nó nhận `mediaId`, lock intent và đọc
asset bằng `public_id` đã lưu. Backend kiểm tra:

- caller là uploader của intent;
- intent chưa hết hạn và chưa bị hủy;
- `public_id`, `asset_id` nếu đã bind, `resource_type=image`, `type=upload`;
- signed context `media_id` và `purpose`;
- actual `format` thuộc allowlist;
- actual `bytes`, `width`, `height` phù hợp policy của purpose;
- `created_at` nằm trong cửa sổ hợp lý của intent;
- secure URL được lấy từ provider hoặc tự generate, không lấy từ FE.

Cloudinary standard resource response trả `format` chứ không cung cấp một canonical original MIME field đủ
để so khớp chính xác với `Content-Type` do browser khai báo. Trong M1 nên dùng `resource_type=image` + format
allowlist + metadata thực tế. Nếu có yêu cầu kiểm tra MIME byte-level, cần một spike riêng về download/header
inspection và chi phí I/O.

### 7.3. Idempotency

**[DESIGN]** Complete lần đầu bind immutable `asset_id` và metadata. Retry sau đó:

- cùng `asset_id`: trả Media view hiện tại;
- asset khác hoặc provider identity mismatch: trả conflict và đưa asset lạ vào cleanup;
- asset chưa thấy: trả trạng thái retryable trong một khoảng ngắn, không tạo record mới.

## 8. Lifecycle và state machine đề xuất

### 8.1. Ba chiều trạng thái

| Chiều | Trạng thái đề xuất | Ghi chú |
| --- | --- | --- |
| Upload | `PENDING_UPLOAD`, `UPLOADED` | `EXPIRED` có thể là terminal state hoặc row được xóa sau audit/grace period |
| Processing | `PENDING`, `PROCESSING`, `READY`, `FAILED` | `PENDING` cho intent chưa có complete/webhook |
| Attachment | `TEMPORARY`, `ACTIVE`, `PENDING_DELETE` | Giữ semantics hiện tại |

Không gộp các chiều này thành một enum lớn vì webhook, complete và attachment diễn ra độc lập.

### 8.2. Transition chính

```text
create intent
  upload=PENDING_UPLOAD, processing=PENDING, attachment=TEMPORARY

complete trước webhook
  upload: PENDING_UPLOAD -> UPLOADED
  processing: PENDING -> PROCESSING

webhook trước complete
  processing: PENDING -> READY | FAILED
  upload vẫn PENDING_UPLOAD cho tới khi backend verify asset

complete sau webhook
  upload: PENDING_UPLOAD -> UPLOADED
  processing giữ READY | FAILED

attach
  chỉ khi upload=UPLOADED
  attachment: TEMPORARY -> ACTIVE

delete/detach
  attachment -> PENDING_DELETE
  physical delete chạy async/idempotent
```

**[DESIGN]** Đề xuất chốt D08: cho phép attach sau khi upload đã được verify, kể cả processing đang
`PROCESSING` hoặc đã `FAILED`. API trả processing status; consumer dùng original URL làm fallback. Processing
failure không tự động chứng minh original asset không hợp lệ.

### 8.3. Race handling

- Intent phải commit trước khi trả signature, nên webhook có thể lookup bằng `public_id` ngay cả khi FE chưa
  gọi complete.
- `/complete`, webhook và cleanup lock cùng Media row hoặc dùng conditional update/version column.
- Complete không ghi đè `READY/FAILED` đã được webhook đặt.
- Duplicate webhook cùng terminal outcome là no-op. Event terminal mâu thuẫn không ghi đè tự động; log và
  đưa vào reconciliation.
- Cleanup chỉ claim intent/media bằng atomic state transition trước khi gọi provider.

### 8.4. Callback bị mất

**[DESIGN]** Một scheduled reconciliation chỉ scan Media `PROCESSING` quá timeout theo batch nhỏ, rồi gọi
Admin API để xem expected derived transformations. Nếu đủ variants thì chuyển `READY`; nếu chưa đủ thì retry
có giới hạn, sau ngưỡng mới chuyển `FAILED`. Cần giới hạn concurrency vì Admin API rate limited.

## 9. D06/D07 — persistence recommendation

### D06: tạo record trước upload

**Đủ cơ sở để chốt: Có.** Record trước upload là điều kiện để:

- backend sở hữu `mediaId`/`public_id`;
- authorize complete/delete;
- nhận webhook trước complete;
- theo dõi và cleanup upload bỏ dở;
- ngăn FE tạo Media bằng URL tùy ý.

### D07: cùng bảng `media` hay bảng riêng

**[DESIGN] Đề xuất dùng cùng bảng `media` trong M1.** Upload intent là giai đoạn đầu của cùng một Media,
không phải aggregate độc lập. Một row giúp webhook, complete, attachment và cleanup cạnh tranh trên cùng lock
và tránh bước chuyển dữ liệu từ bảng intent sang Media.

Schema sau cùng cần cho phép metadata chỉ có sau upload:

- `external_id/public_id` có ngay và vẫn non-null/unique;
- `url`, actual content metadata và `asset_id` nullable khi `PENDING_UPLOAD`;
- thêm expiry, upload status, expected metadata và actual metadata cần cho policy/audit;
- thêm check constraints cho các tổ hợp quan trọng nếu MySQL/Liquibase baseline hỗ trợ phù hợp;
- giữ `uploaded_by_user_id`, `purpose` immutable;
- không dùng attachment `ACTIVE` nếu upload chưa `UPLOADED`.

Bảng riêng chỉ đáng chọn nếu upload intent có retention/audit/rate khác biệt lớn hoặc nhiều intent không bao giờ
trở thành Media đến mức gây vấn đề vận hành. Chưa có nhu cầu đó trong M1.

## 10. TTL và cleanup

**[DOC]** Cloudinary upload signature hợp lệ một giờ. Application không thể coi signature đã bị thu hồi chỉ
vì intent TTL ngắn hơn.

**[DESIGN]** TTL/cleanup phải dựa trên provider signature expiry:

- `expiresAt` trả cho FE nên phản ánh đúng cửa sổ Cloudinary, tối đa một giờ;
- intent bị app từ chối sau expiry;
- không xóa hẳn tombstone/record và kết luận không có orphan trước
  `signatureTimestamp + 1 giờ + grace period`;
- nếu user cancel sớm, đánh dấu canceled nhưng tiếp tục reconciliation tới hết cửa sổ replay;
- signed `overwrite=false` để request replay không thay thế asset đã bind;
- temporary uploaded media có TTL riêng, đề xuất giữ 24 giờ như flow hiện tại.

Giá trị grace period và retention cuối cùng thuộc D11, cần chốt theo UX và tần suất scheduler.

## 11. D13 — quyết định đã chốt

- Mỗi Media thuộc tối đa một business entity và một usage scope.
- `PRODUCT_IMAGE` và `PRODUCT_DESCRIPTION_IMAGE` dùng Media độc lập.
- Cùng file dùng ở hai scope phải upload thành hai Media.
- Không đổi purpose hoặc chuyển owner sau attach.
- Không shared media, cross-scope reuse hoặc reference counting trong M1.
- Reference trùng trong cùng một description được deduplicate thành tập `mediaId` khi đồng bộ attachment.
- Chỉ detach rich-text Media sau khi transaction lưu description thành công.

## 12. Trạng thái các quyết định

### Đủ cơ sở kỹ thuật để đề xuất chốt

| ID | Đề xuất |
| --- | --- |
| D06 | Tạo Media/upload intent trước upload |
| D07 | Dùng cùng bảng `media` trong M1 |
| D08 | Cho attach sau khi upload verified; processing có thể `PROCESSING` hoặc `FAILED` |
| D09 | Correlate webhook chính bằng backend-issued `public_id`; bind thêm `asset_id`, lưu `batch_id` phụ trợ |
| D10 | Verify qua Admin API theo stored `public_id`; không tin metadata/URL từ FE |
| D13 | Đã chốt one owner + one usage scope như mục 11 |

### Còn cần quyết định hoặc xác minh thêm

- D11: business TTL và grace period cụ thể; kỹ thuật yêu cầu tôn trọng cửa sổ signature một giờ.
- D12: processing timeout, batch size, retry limit và điều kiện xác định derived variants đã đủ.
- D15/D16: role, quota, rate limit, allowlist format, max bytes/dimensions và GIF/SVG policy.
- D17: polling interval/timeout và response contract cho FE.
- Folder mode của Cloudinary environment và mapping rõ giữa `public_id` với `asset_folder`.
- Exact MIME policy nếu `resource_type + format` chưa đủ cho yêu cầu bảo mật.
- Failure webhook payload thực tế và reconciliation khi derived processing thất bại một phần.

## 13. Bước tiếp theo

1. Review và chốt D06/D07/D08/D09/D10 theo đề xuất của spike.
2. Chốt policy còn lại ở D11/D12/D15/D16/D17.
3. Viết implementation plan cho PR B, gồm API contract, schema constraints, transition guards và migration
   compatibility.
4. Chưa implement PR B–F trước khi các quyết định trên được duyệt.

## 14. Tài liệu Cloudinary chính

- [Generating authentication signatures](https://cloudinary.com/documentation/authentication_signatures)
- [Upload API reference](https://cloudinary.com/documentation/image_upload_api_reference)
- [Upload presets](https://cloudinary.com/documentation/upload_presets)
- [Webhooks and notifications](https://cloudinary.com/documentation/notifications)
- [Verifying notification signatures](https://cloudinary.com/documentation/notification_signatures)
- [Verifying response signatures](https://cloudinary.com/documentation/response_signatures)
- [Admin API reference](https://cloudinary.com/documentation/admin_api)
