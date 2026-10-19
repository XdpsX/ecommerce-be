# Sử dụng Media

## Upload và gắn ảnh

API Media yêu cầu `ADMIN`. Dùng OpenAPI `/v3/api-docs/backoffice` để xem schema đầy đủ.

1. Gọi `POST /media/upload-intents` với metadata:

   ```json
   {"purpose":"PRODUCT_IMAGE","filename":"front.png","contentType":"image/png","sizeBytes":204800}
   ```

2. Upload file trực tiếp tới Cloudinary bằng instructions trả về:

   ```ts
   const body = new FormData();
   for (const [key, value] of Object.entries(intent.upload.fields)) {
     body.append(key, value);
   }
   body.append("file", file);
   const result = await fetch(intent.upload.url, { method: intent.upload.method, body });
   if (!result.ok) throw new Error("Provider upload failed");
   ```

   Giữ nguyên signed fields. Không gửi JWT backend và không tự đặt multipart Content-Type.

3. Gọi `POST /media/{id}/complete`, không gửi body. Backend tự đọc Cloudinary Admin API để verify asset.
4. Khi `uploadStatus=UPLOADED`, gửi `mediaId` vào API Category/Brand/Product để gắn ảnh.
   Không cần chờ processing `READY`; `PROCESSING` hoặc `FAILED` vẫn gắn được nếu các guard khác hợp lệ.
5. Đọc `GET /media/{id}` để cập nhật trạng thái và URL. Sau khi đã attach, dùng GET thay vì complete lại.

`POST /media/image-upload` vẫn hoạt động như đường multipart tương thích, chưa deprecated.

## Trạng thái và quyền sử dụng

| Chiều | Trạng thái |
| --- | --- |
| Upload | `PENDING_UPLOAD` → `UPLOADED` sau backend verification |
| Processing | `PENDING` → `PROCESSING` → `READY` hoặc `FAILED`; callback sớm có thể đi thẳng tới terminal |
| Attachment | `TEMPORARY` → `ACTIVE` → `PENDING_DELETE`; cancel/expire cũng có thể claim từ TEMPORARY |

- Chỉ attach Media `UPLOADED + TEMPORARY`, đúng purpose và uploader. Một Media thuộc tối đa một entity.
- Owner và purpose không được chuyển. Detach giữ owner, chuyển `PENDING_DELETE`; không attach lại.
- Uploader khác gọi status/complete/delete nhận `404`. Cancel bằng DELETE trả `204`, chỉ claim cleanup.
- URL gốc có thể null trước verification. Dùng original khi processing chưa READY hoặc FAILED;
  `variants` chỉ có khi `UPLOADED + READY`.

| Purpose | Variant | Chiều rộng nguồn tối thiểu |
| --- | --- | ---: |
| `CATEGORY_IMAGE` | `categoryCard` | 280 |
| `BRAND_LOGO` | `brandLogo` | 280 |
| `PRODUCT_IMAGE` | `productCard`, `productDetail` | 560 |
| `PRODUCT_DESCRIPTION_IMAGE` | `productContent` | Không đặt |

Gallery và description dùng Media riêng. Backend chưa parse/sanitize rich text hoặc đồng bộ attachment trong description.
Editor remove/Undo/Cancel không được xóa Media đang được persisted content tham chiếu.

## Retry và expiry

- Poll mỗi 2 giây, tối đa 60 giây/lượt; dừng khi terminal hoặc PENDING_DELETE. UI timeout không đổi backend state.
- Upload timeout/CORS error có thể xảy ra sau khi provider đã nhận file. Thử complete có giới hạn trên cùng intent;
  GET status không tự verify Cloudinary. Không lặp tạo intent mới vô hạn.
- `intentExpiresAt` là expiry logic; `uploadAuthorizationExpiresAt` là expiry chữ ký, không suy ra từ nhau.
  Default signature 1 giờ, complete grace 10 phút. Complete retry thành công chỉ được bảo đảm khi còn TEMPORARY.

| Lỗi backend | Xử lý |
| --- | --- |
| `400 VALIDATION_FAILED` | Sửa metadata request; mặc định JPEG/PNG/GIF, tối đa 2 MiB |
| `409 MEDIA_UPLOAD_CONFLICT` | Đọc status; chỉ retry có giới hạn khi còn completable, không retry mọi 409 |
| `409 MEDIA_UPLOAD_EXPIRED` | Dừng intent cũ; tạo mới khi người dùng muốn thử lại |
| `422 MEDIA_ASSET_INVALID` | Asset bị reject; không retry complete trên intent cũ |
| `503 MEDIA_PROVIDER_UNAVAILABLE` | Retry complete cùng intent với backoff, trong lifecycle/window cho phép |
| `409 MEDIA_ATTACHMENT_CONFLICT` | Kiểm tra owner/lifecycle; chờ variants không sửa được lỗi này |

Lỗi Cloudinary/browser không mang application error code của backend.

## Vì sao thiết kế như vậy?

- Lưu intent trong bảng `media` trước khi trả chữ ký: webhook sớm có identity để tìm; M1 chưa cần bảng intent riêng.
- Backend cấp `public_id` để correlate; `asset_id` bind bất biến; `batch_id` chỉ phụ trợ.
- Complete kiểm tra identity, context, format, bytes, dimensions và thời gian qua Admin API; không tin metadata FE.
- Upload và processing độc lập để ảnh gốc vẫn dùng được khi eager chưa xong hoặc thất bại.

Spike Cloudinary ngày 14/10/2026 đã ghi nhận: đổi public ID bị 401, signed upload hợp lệ thành công,
Admin API đọc được metadata/context, eager callback có public/asset/batch ID nhưng không có context.
Đây là evidence spike cũ; failure payload, raw naming và upload sát expiry vẫn cần smoke theo môi trường.

Xem [test và vận hành](operations.md) khi setup webhook hoặc rollout.
