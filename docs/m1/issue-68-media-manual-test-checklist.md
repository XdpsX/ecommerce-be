# Issue #68 — Checklist test Media và Cloudinary Eager Async

Tài liệu này dùng để test thủ công flow media sau khi backend chạy local. Mục tiêu là kiểm tra cả upload, validation, trạng thái database, eager variants, webhook và cleanup.

## 1. Chuẩn bị

### 1.1. Environment

- [ ] Database local đang chạy và Liquibase đã áp dụng changeset-26.sql.
- [ ] CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET và CLOUDINARY_CLOUD_NAME trỏ tới cùng một Cloudinary account.
- [ ] CLOUDINARY_EAGER_NOTIFICATION_URL là URL HTTPS hợp lệ.
- [ ] Backend start thành công ở port 8080.
- [ ] Có tài khoản admin và access token hợp lệ.
- [ ] Nếu test callback tự động, tunnel đang forward tới port 8080.
- [ ] Nếu chưa có tunnel, dùng workaround replay trong [Cloudinary webhook guide](issue-68-cloudinary-webhook-guide.md#6-workaround-ngan-han-khi-chua-setup-duoc-tunnel).

### 1.2. Endpoint

Upload media:

~~~text
POST /media/image-upload?resource={resource}
Content-Type: multipart/form-data
Authorization: Bearer <admin-token>
~~~

Các field multipart:

~~~text
caption: optional
file: required
~~~

Webhook:

~~~text
POST /webhooks/cloudinary/eager
~~~

Delete temporary media:

~~~text
DELETE /media/{id}
Authorization: Bearer <admin-token>
~~~

### 1.3. Bộ file test

Chuẩn bị ít nhất:

| File | Nội dung dùng để test |
| --- | --- |
| JPEG hợp lệ rộng 600px trở lên | Product image đạt minimum width |
| JPEG hợp lệ rộng 300px | Category/brand đạt minimum width |
| JPEG hợp lệ rộng 100px | Product description vẫn được chấp nhận vì không có minimum width |
| JPEG hợp lệ rộng dưới 280px | Category/brand bị reject |
| JPEG hợp lệ rộng dưới 560px | Product bị reject |
| PNG và GIF hợp lệ | Các MIME type được cho phép |
| File text hoặc bytes ngẫu nhiên đổi tên thành .jpg | Kiểm tra dữ liệu thực sự phải decode được thành ảnh |
| File lớn hơn 2MB | Kiểm tra multipart size limit |

Không dùng file chứa dữ liệu nhạy cảm khi gửi qua tunnel hoặc webhook collector.

## 2. Smoke test application và configuration

### Case S-01 — Application start với cấu hình hợp lệ

Thực hiện:

1. Đặt CLOUDINARY_EAGER_NOTIFICATION_URL là HTTPS URL hợp lệ.
2. Start backend.

Kỳ vọng:

- Application start thành công.
- Cloudinary client và webhook controller được tạo.
- Không có lỗi No default constructor found của CloudinaryNotificationSignatureVerifier.
- Không có lỗi validation eager-notification-url.

### Case S-02 — Thiếu webhook URL

Thực hiện:

1. Để CLOUDINARY_EAGER_NOTIFICATION_URL rỗng hoặc không khai báo.
2. Restart backend.

Kỳ vọng:

- Application fail startup.
- Log chỉ rõ eager-notification-url không được để trống.

### Case S-03 — Webhook URL dùng HTTP

~~~env
CLOUDINARY_EAGER_NOTIFICATION_URL=http://localhost:8080/webhooks/cloudinary/eager
~~~

Kỳ vọng:

- Application fail startup vì URL không phải HTTPS.

## 3. Upload API và validation

Endpoint dùng cho các case dưới đây:

~~~text
POST http://localhost:8080/media/image-upload?resource=<resource>
~~~

Gửi multipart field file và tùy chọn caption, kèm admin Bearer token.

### Case U-01 — Upload product image hợp lệ

Input:

~~~text
resource=product
file=ảnh JPEG rộng từ 560px trở lên
caption=Product main image
~~~

Kỳ vọng HTTP:

- Status 201 Created.
- Response có id, url, contentType, caption.
- url là original source URL.
- variants có đúng productCard và productDetail.

Kỳ vọng database:

- attachment_status = TEMPORARY.
- processing_status = PROCESSING ngay sau upload.
- processing_reference không null và không trùng media khác.
- external_id và url được lưu.

### Case U-02 — Upload category image hợp lệ

~~~text
resource=category
file=ảnh rộng từ 280px trở lên
~~~

Kỳ vọng:

- Status 201.
- variants chỉ có categoryCard.
- Database lưu purpose tương ứng và bắt đầu ở PROCESSING.

### Case U-03 — Upload brand logo hợp lệ

~~~text
resource=brand
file=ảnh rộng từ 280px trở lên
~~~

Kỳ vọng:

- Status 201.
- variants chỉ có brandLogo.

### Case U-04 — Upload product description image nhỏ

~~~text
resource=product-description
file=ảnh JPEG hợp lệ rộng khoảng 100px
~~~

Kỳ vọng:

- Status 201.
- Không bị reject vì width nhỏ.
- variants chỉ có productContent.
- Đây là ảnh thật và decode được, không phải file bytes giả.

### Case U-05 — Upload resource không hợp lệ

~~~text
resource=unknown
file=ảnh hợp lệ
~~~

Kỳ vọng:

- Request bị reject theo API contract, thường là 422.
- Không có upload mới lên Cloudinary.
- Không có row Media mới trong database.

### Case U-06 — Thiếu file

Thực hiện: bỏ field file.

Kỳ vọng:

- Status 400.
- Không upload provider và không tạo Media row.

### Case U-07 — MIME type không được cho phép

Thực hiện: gửi PDF, WebP hoặc file MIME khác JPEG/PNG/GIF.

Kỳ vọng:

- Status 400 do FileTypeConstraint.
- Không upload provider.

### Case U-08 — Dữ liệu không phải ảnh nhưng giả MIME/image extension

Thực hiện: gửi text hoặc bytes ngẫu nhiên với filename .jpg và content type image/jpeg.

Kỳ vọng:

- Status 400 hoặc lỗi validation tương ứng.
- Không upload provider.

### Case U-09 — Width nhỏ hơn minimum

Chạy riêng cho:

| Resource | Minimum width | File test |
| --- | ---: | --- |
| category | 280px | 100px |
| brand | 280px | 100px |
| product | 560px | 300px |

Kỳ vọng:

- Status 400 với lỗi invalid image width.
- Error detail có minWidth tương ứng.
- Không upload provider.

### Case U-10 — File lớn hơn giới hạn

Thực hiện: gửi file lớn hơn 2MB.

Kỳ vọng:

- Request bị reject ở multipart layer, thường là 413 hoặc error response tương ứng.
- Không upload provider.

### Case U-11 — Không có admin authentication

Thực hiện: gọi upload không có token hoặc dùng user không có role ADMIN.

Kỳ vọng:

- Request bị từ chối bởi security, thường là 401 hoặc 403.
- Không upload provider.

## 4. Kiểm tra Cloudinary upload options

Có thể kiểm tra qua log/provider dashboard hoặc test adapter. Không cần kiểm tra tất cả case này qua UI nếu đã có focused tests, nhưng nên kiểm tra một lần bằng upload thật.

### Case C-01 — Eager async được bật

Sau một upload hợp lệ, xác nhận options gửi lên Cloudinary có:

~~~text
eager_async = true
eager_notification_url = <HTTPS webhook URL>
~~~

Không được dùng URL localhost hoặc HTTP.

### Case C-02 — Preset product image

Xác nhận eager transformations của resource=product gồm:

| Variant | Transformation chính |
| --- | --- |
| productCard | c_fill,g_auto,h_600,q_auto,w_600 |
| productDetail | c_fit,h_1200,q_auto,w_1200 |

Không có f_auto.

### Case C-03 — Preset product content

Xác nhận resource=product-description dùng:

~~~text
c_limit,q_auto,w_1200
~~~

Không crop ảnh và không yêu cầu minimum source width.

### Case C-04 — Preset category và brand

| Resource | Variant | Transformation chính |
| --- | --- | --- |
| category | categoryCard | c_fill,g_auto,h_400,q_auto,w_600 |
| brand | brandLogo | c_fit,h_400,q_auto,w_400 |

## 5. Webhook và processing lifecycle

Để test callback thật, dùng tunnel. Nếu chưa setup được tunnel, dùng webhook collector và replay raw request theo [hướng dẫn workaround](issue-68-cloudinary-webhook-guide.md#6-workaround-ngan-han-khi-chua-setup-duoc-tunnel).

### Case W-01 — Notification success

Thực hiện:

1. Upload media hợp lệ.
2. Ghi lại processing_reference/batch_id.
3. Chờ Cloudinary gửi eager notification success.

Kỳ vọng:

- Webhook response 200.
- Media chuyển PROCESSING → READY.
- processing_failure_reason vẫn null.
- attachment_status không bị thay đổi bởi webhook.

### Case W-02 — Notification failure

Thực hiện: dùng payload failure thật hoặc replay payload failure có batch_id trùng media đang PROCESSING.

Kỳ vọng:

- Webhook response 200 nếu payload và signature hợp lệ.
- Media chuyển PROCESSING → FAILED.
- processing_failure_reason được lưu và bị giới hạn tối đa 500 ký tự.

### Case W-03 — Thiếu signature

Thực hiện: gọi webhook không có X-Cld-Signature hoặc X-Cld-Timestamp.

Kỳ vọng:

- Status 401.
- Media không thay đổi trạng thái.

### Case W-04 — Sai signature hoặc body bị thay đổi

Thực hiện:

- Sửa một ký tự trong X-Cld-Signature; hoặc
- giữ signature cũ nhưng sửa raw JSON body.

Kỳ vọng:

- Status 401.
- Không parse/mutate Media.

### Case W-05 — Timestamp quá cũ

Thực hiện: dùng timestamp cũ hơn CLOUDINARY_WEBHOOK_TIMESTAMP_TOLERANCE.

Kỳ vọng:

- Status 401.
- Không cập nhật processing status.

### Case W-06 — Signed payload malformed

Thực hiện: gửi JSON malformed nhưng tạo signature hợp lệ cho chính raw body đó.

Kỳ vọng:

- Status 400.
- Không cập nhật Media.

### Case W-07 — Notification đến trước khi Media row visible

Thực hiện: dùng batch_id hợp lệ nhưng chưa có Media row tương ứng.

Kỳ vọng:

- Status 503.
- Log ghi nhận media chưa visible.
- Không tạo row mới.

### Case W-08 — Duplicate success

Thực hiện: gửi cùng success notification hai lần.

Kỳ vọng:

- Cả hai request đều được acknowledge thành công.
- Lần thứ hai không thay đổi READY hoặc các field khác.

### Case W-09 — Conflicting terminal notification

Thực hiện:

1. Gửi success để Media thành READY.
2. Gửi failure với cùng batch_id.
3. Test ngược lại với Media bắt đầu ở FAILED.

Kỳ vọng:

- Terminal state đầu tiên thắng.
- READY không bị đổi thành FAILED.
- FAILED không bị đổi thành READY.
- Conflict được log.

### Case W-10 — Concurrent terminal notifications

Thực hiện: gửi success và failure gần như đồng thời cho cùng batch_id.

Kỳ vọng:

- Chỉ một terminal transition được áp dụng.
- Kết quả cuối cùng không bị ghi đè ngẫu nhiên sau khi đã terminal.
- Không phát sinh exception do race condition.

## 6. URL và variant output

### Case V-01 — Original URL

- [ ] url trong upload response trỏ tới original Cloudinary asset.
- [ ] Original URL mở được sau upload.
- [ ] url không bị thay bằng URL của một eager variant.

### Case V-02 — Variant URL theo purpose

| Resource | Variant phải có |
| --- | --- |
| product | productCard, productDetail |
| product-description | productContent |
| category | categoryCard |
| brand | brandLogo |

- [ ] Variant URL dùng cùng externalId/public ID của original.
- [ ] Variant URL không chứa f_auto.
- [ ] Variant URL mở được sau khi Cloudinary eager processing hoàn tất.
- [ ] Không có variant name không phù hợp với purpose.

### Case V-03 — Catalog contract không bị thay đổi

Kiểm tra product/category/brand read APIs sau khi attach media:

- [ ] Các response catalog cũ vẫn giữ shape cũ.
- [ ] variants chỉ xuất hiện trong upload media response, không bị thêm ngoài contract đã chốt.

## 7. Delete và cleanup

### Case D-01 — Delete temporary media

Thực hiện:

1. Upload media thành công nhưng chưa attach vào product/category/brand.
2. Gọi DELETE /media/{id}.

Kỳ vọng:

- Status 204 No Content.
- Attachment status chuyển sang PENDING_DELETE.
- Scheduler có thể cleanup provider asset theo TTL/cleanup flow.

### Case D-02 — Delete media không tồn tại

Kỳ vọng:

- Status 404.
- Không có provider delete call hợp lệ.

### Case D-03 — Không xóa media đã active qua Media API

Thực hiện: attach media vào aggregate trước, sau đó gọi DELETE /media/{id}.

Kỳ vọng:

- Request bị từ chối theo contract hiện tại.
- Media active không bị xóa khỏi provider.

### Case D-04 — Cleanup provider failure

Đây là case phù hợp với integration/focused test hơn là thao tác UI:

- [ ] Provider delete trả failure không làm scheduler dừng toàn bộ batch.
- [ ] Media chỉ được mark/delete khi provider operation đạt kết quả phù hợp.
- [ ] Failure được log để retry hoặc điều tra.

## 8. Kiểm tra database sau mỗi test quan trọng

Sau U-01 hoặc W-01, chạy query tương đương:

~~~sql
SELECT
    id,
    purpose,
    attachment_status,
    processing_status,
    processing_failure_reason,
    processing_reference,
    external_id,
    url
FROM media
ORDER BY created_at DESC
LIMIT 10;
~~~

Các invariant cần kiểm tra:

- [ ] Media upload mới có attachment_status = TEMPORARY.
- [ ] Media upload mới bắt đầu với processing_status = PROCESSING.
- [ ] processing_reference không null đối với upload async.
- [ ] processing_reference là duy nhất.
- [ ] Success chỉ chuyển processing status sang READY.
- [ ] Failure chỉ chuyển processing status sang FAILED và lưu reason.
- [ ] Webhook không tự thay đổi attachment status.
- [ ] Duplicate/conflict không ghi đè terminal state.

## 9. Regression checklist

Sau khi hoàn tất manual test, chạy focused automated tests:

~~~powershell
.\mvnw.cmd '-Dtest=MediaServiceImplTest,CloudinaryMediaStorageTest,CloudinaryMediaUrlGeneratorTest,CloudinaryNotificationSignatureVerifierTest,CloudinaryWebhookControllerTest,CloudinaryEagerWebhookHandlerTest,MediaCleanUpSchedulerTest,MediaAttachmentConcurrencyTest' test
~~~

Kiểm tra formatting và diff:

~~~powershell
.\mvnw.cmd spotless:check
git diff --check
~~~

Nếu chạy migration test cần Docker/Testcontainers:

~~~powershell
.\mvnw.cmd '-Dtest=MediaProcessingMigrationTest' test
~~~

Nếu Docker không khả dụng, ghi rõ migration test chưa chạy thay vì đánh dấu toàn bộ migration là đã verified.

## 10. Kết quả cần ghi lại

Khi thực hiện, nên ghi theo bảng sau:

| Case | Kết quả | HTTP | DB state | Evidence |
| --- | --- | ---: | --- | --- |
| U-01 | PASS/FAIL |  |  | response/log/screenshot |
| U-04 | PASS/FAIL |  |  |  |
| U-08 | PASS/FAIL |  |  |  |
| W-01 | PASS/FAIL |  |  | Cloudinary/tunnel/log |
| W-02 | PASS/FAIL |  |  |  |
| W-04 | PASS/FAIL |  |  |  |
| W-08 | PASS/FAIL |  |  |  |
| W-09 | PASS/FAIL |  |  |  |
| V-02 | PASS/FAIL |  |  | variant URLs |
| D-01 | PASS/FAIL |  |  |  |

Evidence tối thiểu cho một flow end-to-end thành công:

1. Upload response 201.
2. Row Media với PROCESSING và processing_reference.
3. Cloudinary notification có cùng batch_id.
4. Webhook response 200.
5. Row Media sau callback với READY.
6. Original URL và các variant URL truy cập được.
