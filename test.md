Bạn chạy checklist này bằng **Postman**, dùng ảnh thử và hai tài khoản ADMIN khác nhau. Browser/Category sẽ kiểm tra riêng sau.

**Chuẩn bị**

Tạo Postman environment:

| Biến          | Giá trị                                              |
| ------------- | ---------------------------------------------------- |
| `baseUrl`     | `http://localhost:8080` hoặc địa chỉ backend thực tế |
| `adminAToken` | Access token ADMIN A                                 |
| `adminBToken` | Access token ADMIN B                                 |
| `mediaId`     | Lưu sau bước tạo intent                              |
| `uploadUrl`   | Lưu từ response tạo intent                           |

Chọn một file PNG/JPEG, rộng **ít nhất 280 px**, dung lượng dưới giới hạn cấu hình — mặc định **2 MiB**. Checklist dùng `CATEGORY_IMAGE`.

Lấy token nếu chưa có:

```http
POST {{baseUrl}}/auth/login
Content-Type: application/json
```

```json
{
  "email": "<email-admin>",
  "password": "<password>"
}
```

Kỳ vọng `200`, lấy trường `accessToken`. Thực hiện cho cả A và B; đăng ký thông thường không mặc định tạo ADMIN.

Để kiểm tra được `READY`, cấu hình Cloudinary thật và callback public HTTPS tới:

```text
/webhooks/cloudinary/eager
```

Xem [hướng dẫn webhook local](C:/Users/DungNC/Desktop/commerce/ecommerce-be/docs/media/operations.md). Sau khi thay URL callback, restart backend **trước khi tạo intent mới**. Giữ reconciliation/cleanup disabled trong lượt smoke này.

**1. Tạo intent — ADMIN A**

```http
POST {{baseUrl}}/media/upload-intents
Authorization: Bearer {{adminAToken}}
Content-Type: application/json
```

Body raw JSON:

```json
{
  "purpose": "CATEGORY_IMAGE",
  "filename": "smoke-category.png",
  "contentType": "image/png",
  "sizeBytes": 204800
}
```

Thay filename, MIME và `sizeBytes` bằng metadata của **file thực tế**; `204800` chỉ là ví dụ.

Kỳ vọng:

- HTTP `201`.
- Có `mediaId`, hai timestamp expiry và `upload`.
- `upload.provider = CLOUDINARY`, `upload.method = POST`.
- Trạng thái ban đầu:

```json
{
  "status": {
    "upload": "PENDING_UPLOAD",
    "processing": "PENDING",
    "attachment": "TEMPORARY"
  }
}
```

Có thể đặt script **Post-response** để lưu biến:

```javascript
pm.response.to.have.status(201);
const intent = pm.response.json();
pm.environment.set("mediaId", intent.mediaId);
pm.environment.set("uploadUrl", intent.upload.url);
```

**2. Upload bytes trực tiếp tới Cloudinary**

Tạo request riêng:

```http
POST {{uploadUrl}}
```

Trong Postman:

- Authorization: **No Auth**, không inherit JWT từ collection.
- Body: **form-data**.
- Copy **toàn bộ** từng key/value trong `upload.fields` ở bước 1 thành các dòng kiểu **Text**, giữ nguyên giá trị.
- Thêm dòng `file`, chọn kiểu **File** và chọn đúng ảnh.
- Không tự đặt header `Content-Type`; Postman tự tạo multipart boundary.

Các fields hiện tại gồm:

```text
api_key
timestamp
public_id
overwrite
allowed_formats
eager
eager_async
eager_notification_url
context
signature
```

Luôn lấy từ response, không tự viết lại signature hoặc transformation.

Kỳ vọng request upload thành công (`200`), response Cloudinary có identity asset và URL. `public_id` khớp field backend cấp.

**Chưa coi Media là `UPLOADED` ở backend chỉ vì Cloudinary trả thành công.**

**3. Complete — ADMIN A**

```http
POST {{baseUrl}}/media/{{mediaId}}/complete
Authorization: Bearer {{adminAToken}}
```

Body: **none**. Không gửi URL, public ID hoặc response Cloudinary vào body.

Kỳ vọng `200`:

```json
{
  "mediaId": "<mediaId ở bước 1>",
  "purpose": "CATEGORY_IMAGE",
  "uploadStatus": "UPLOADED",
  "processingStatus": "PROCESSING",
  "attachmentStatus": "TEMPORARY",
  "originalUrl": "<URL ảnh gốc>",
  "variants": {}
}
```

`processingStatus` có thể đã là `READY` nếu webhook đến sớm; đó cũng là kết quả hợp lệ.

- Nếu `PROCESSING`: original URL có giá trị, variants rỗng.
- Nếu `READY`: variants có `categoryCard`.
- Mở `originalUrl` để xác nhận ảnh đúng file đã upload.

Gọi complete thêm một lần khi Media còn `TEMPORARY`: kỳ vọng vẫn `200`, cùng `mediaId` và `UPLOADED`. Processing có thể tiến triển giữa hai lần gọi.

**4. Đọc status và kiểm tra webhook**

```http
GET {{baseUrl}}/media/{{mediaId}}
Authorization: Bearer {{adminAToken}}
```

Body: **none**.

Gửi lại khoảng mỗi 2 giây, tối đa 60 giây cho lượt kiểm tra.

Kỳ vọng khi callback thành công:

- `200`, `uploadStatus = UPLOADED`.
- `processingStatus = READY`.
- `attachmentStatus = TEMPORARY` vì chưa lưu Category.
- `variants.categoryCard` có URL; mở được ảnh.
- Backend nhận callback Cloudinary hợp lệ.

Không tự giả lập callback bằng cách POST JSON không có chữ ký: endpoint cần raw body cùng `X-Cld-Signature` và `X-Cld-Timestamp`.

Nếu sau 60 giây vẫn `PROCESSING`, ghi nhận **webhook/processing chưa xác minh**, kiểm tra callback và log; timeout không được diễn giải thành backend `FAILED`. Nếu nhận `FAILED`, ghi nhận lỗi processing, kiểm tra original fallback; chưa đánh dấu happy path variants đạt.

**5. Cross-uploader — ADMIN B**

Giữ nguyên `mediaId` của A và lần lượt gửi bằng token B:

| Request                            | Body | Kỳ vọng |
| ---------------------------------- | ---- | ------- |
| `GET /media/{{mediaId}}`           | none | `404`   |
| `POST /media/{{mediaId}}/complete` | none | `404`   |
| `DELETE /media/{{mediaId}}`        | none | `404`   |

Sau đó dùng **A** GET lại:

- Vẫn `200`.
- Vẫn `UPLOADED + TEMPORARY`.
- Không bị chuyển `PENDING_DELETE` do request của B.
- Processing có thể đổi do webhook, không cần giữ nguyên giá trị đó.

Nếu gặp lỗi trong luồng chính:

| Lỗi backend                      | Cách xử lý                                                                              |
| -------------------------------- | --------------------------------------------------------------------------------------- |
| `409 MEDIA_UPLOAD_CONFLICT`      | Kiểm tra upload provider đã thành công chưa và đọc status; không retry mọi `409`        |
| `409 MEDIA_UPLOAD_EXPIRED`       | Dừng intent cũ, tạo lượt mới                                                            |
| `422 MEDIA_ASSET_INVALID`        | Kiểm tra file/dimensions/metadata; không lặp complete trên intent bị reject             |
| `503 MEDIA_PROVIDER_UNAVAILABLE` | Retry complete trên cùng Media với backoff và giới hạn, khi còn trong completion window |

Provider upload timeout có thể xảy ra sau khi Cloudinary đã nhận file. Thử complete trên cùng Media để backend xác minh trước khi xin intent mới; GET đơn thuần không inspect provider.

**Các bước vẫn pending**

- **Browser:** CORS từ origin FE thật, signed fields nguyên vẹn, không JWT tới Cloudinary, multipart boundary, preview/fallback, polling và recovery khi network timeout.
- **Category:** lưu `mediaId` khi `UPLOADED + PROCESSING` vẫn thành công; sau lưu Media thành `ACTIVE`; variants xuất hiện sau mà không cần lưu Category lại.
- Owner audit/repair và controlled worker activation thuộc rollout riêng; checklist này chưa xác nhận các phần đó.

Tôi đã đối chiếu controller, DTO, service, signed fields và tài liệu hiện tại; chưa gửi request thật, không sửa file.

**Tiếp theo: Sol đọc kết quả smoke của bạn.** Gửi lại:

```text
Media Postman smoke:
1. Intent: ...
2. Cloudinary upload: ...
3. Complete + complete lặp: ...
4. Status/webhook/categoryCard: ...
5. Cross-uploader GET/complete/delete: ...
Browser/Category: pending.
Lỗi nếu có: HTTP status + application code + response đã che thông tin nhạy cảm.
```
