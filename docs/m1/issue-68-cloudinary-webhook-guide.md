# Issue #68 — Cloudinary webhook: local testing and production setup

## 1. Vì sao cần webhook public?

Issue #68 dùng Cloudinary Eager Async để Cloudinary xử lý các variant ảnh sau khi upload. Backend không thể biết chính xác thời điểm xử lý hoàn tất nếu chỉ dựa vào response upload ban đầu.

Flow chính:

```text
Client
  ↓
Backend upload ảnh lên Cloudinary
  ↓
Cloudinary trả upload response + batch_id
  ↓
Backend lưu Media với processingStatus = PROCESSING
  ↓
Cloudinary xử lý eager transformations bất đồng bộ
  ↓
Cloudinary POST webhook về backend
  ↓
Backend xác thực chữ ký và cập nhật READY / FAILED
```

Webhook của project là:

```text
POST /webhooks/cloudinary/eager
```

Khi chạy local, backend thường chỉ có địa chỉ `http://localhost:8080`. Địa chỉ này chỉ máy local truy cập được, nên Cloudinary trên Internet không thể gọi trực tiếp vào đó. Cần một tunnel để tạo URL HTTPS public và chuyển request về port local:

```text
Cloudinary
  ↓ https://public-tunnel.example
Tunnel
  ↓ http://localhost:8080
Backend local
```

`CLOUDINARY_EAGER_NOTIFICATION_URL` chính là URL đầy đủ mà Cloudinary sẽ gọi:

```env
CLOUDINARY_EAGER_NOTIFICATION_URL=https://public-tunnel.example/webhooks/cloudinary/eager
```

URL phải là HTTPS và phải trỏ đúng tới endpoint trên. Backend xác thực `X-Cld-Signature` và `X-Cld-Timestamp` trước khi xử lý payload.

## 2. CORS có liên quan không?

Không. Cloudinary gọi webhook theo kiểu server-to-server, không phải request từ trình duyệt, nên không cần thêm domain tunnel vào `CORS_ALLOWED_ORIGINS`.

Hai cấu hình phục vụ hai mục đích khác nhau:

| Cấu hình | Dùng cho |
| --- | --- |
| `CLOUDINARY_EAGER_NOTIFICATION_URL` | Cloudinary gọi ngược vào backend |
| `CORS_ALLOWED_ORIGINS` | Frontend chạy trong browser gọi API backend |

Nếu frontend vẫn chạy ở `http://localhost:3000` hoặc `http://localhost:3001`, CORS local hiện tại không cần thay đổi.

## 3. So sánh các lựa chọn tunnel

| Tiêu chí | Cloudflare Quick Tunnel | ngrok Free |
| --- | --- | --- |
| Mục đích phù hợp | Test local nhanh | Test và debug request chi tiết |
| Domain/account | Không cần Cloudflare account hoặc domain | Cần tài khoản và authtoken |
| Cách chạy | `cloudflared tunnel --url http://localhost:8080` | `ngrok http 8080` |
| URL | Random `trycloudflare.com`, đổi khi tạo tunnel mới | Development domain của tài khoản hoặc URL random tùy cấu hình |
| Quan sát request | Xem log cơ bản trong terminal | Có traffic inspection và replay tiện hơn |
| Chi phí test | Miễn phí | Có free plan với giới hạn lưu lượng/request |
| Production | Không dùng Quick Tunnel | Không dùng free tunnel làm production endpoint |

Khuyến nghị cho lần test đầu tiên: **Cloudflare Quick Tunnel** vì không cần thuê host, mua domain hoặc cấu hình DNS. Quick Tunnel chỉ dành cho development/testing, không có uptime guarantee và URL dừng hoạt động khi process tunnel dừng. Xem [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

Chọn ngrok nếu cần xem đầy đủ headers, body, status code và replay webhook trong giao diện debug. Xem [ngrok pricing](https://ngrok.com/pricing) và [ngrok local development](https://ngrok.com/use-cases/share-localhost).

## 4. Cài đặt và test local với Cloudflare Quick Tunnel

Các bước dưới đây dành cho Windows.

### 4.1. Cài `cloudflared`

1. Mở trang [Cloudflare Downloads](https://developers.cloudflare.com/tunnel/downloads/).
2. Tải bản Windows 64-bit nếu máy dùng Windows 64-bit.
3. Cài file MSI hoặc tải executable rồi đặt ở một thư mục nằm trong `PATH`.
4. Mở PowerShell mới và kiểm tra:

```powershell
cloudflared --version
```

Nếu PowerShell in ra version, việc cài đặt đã hoàn tất.

### 4.2. Đảm bảo backend khởi động thành công

Mở PowerShell thứ nhất tại thư mục project:

```powershell
.\mvnw.cmd spring-boot:run
```

Backend phải lắng nghe tại:

```text
http://localhost:8080
```

Nếu application fail startup với lỗi `No default constructor found` của `CloudinaryNotificationSignatureVerifier`, cần đánh dấu constructor production bằng `@Autowired` trước khi chạy tunnel. Tunnel chỉ chuyển request; nó không sửa được lỗi startup của backend.

### 4.3. Tạo tunnel

Mở PowerShell thứ hai:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Terminal sẽ in một URL dạng:

```text
https://random-name.trycloudflare.com
```

Giữ cửa sổ PowerShell này mở trong suốt thời gian test.

### 4.4. Cấu hình `.env`

Đặt URL public kèm đúng path webhook:

```env
CLOUDINARY_EAGER_NOTIFICATION_URL=https://random-name.trycloudflare.com/webhooks/cloudinary/eager
```

Không dùng:

```env
http://localhost:8080/webhooks/cloudinary/eager
http://random-name.trycloudflare.com/webhooks/cloudinary/eager
```

Sau khi sửa `.env`, restart Spring Boot để `CloudinaryMediaProperties` đọc giá trị mới và kiểm tra HTTPS.

### 4.5. Chạy smoke test

1. Đăng nhập frontend như bình thường.
2. Upload một ảnh thuộc purpose có eager variants.
3. Kiểm tra upload response có `batch_id` được lưu dưới `processingReference` và media bắt đầu ở `PROCESSING`.
4. Chờ Cloudinary xử lý xong.
5. Kiểm tra log của tunnel có request `POST /webhooks/cloudinary/eager`.
6. Kiểm tra database: processing status chuyển thành `READY` hoặc `FAILED`.

Cloudinary webhook gửi chữ ký trong `X-Cld-Signature` và timestamp trong `X-Cld-Timestamp`. Vì vậy, không nên dùng một request giả không có chữ ký để kết luận rằng toàn bộ flow đã thành công.

### 4.6. Ý nghĩa các response thường gặp

| HTTP status | Ý nghĩa |
| --- | --- |
| `200` | Webhook hợp lệ và đã được áp dụng, hoặc là duplicate/conflicting terminal event đã được xử lý idempotently |
| `400` | Chữ ký hợp lệ nhưng payload malformed/không hợp lệ |
| `401` | Thiếu hoặc sai chữ ký/timestamp |
| `503` | Media chưa nhìn thấy trong database; cho phép Cloudinary retry do race giữa upload response và transaction insert |
| `404` | Sai path webhook hoặc tunnel/backend route không khớp |
| `502`/connection error | Backend local không chạy hoặc tunnel không forward được tới port `8080` |

## 5. Debug bằng ngrok (lựa chọn thay thế)

Nếu cần quan sát request chi tiết hơn:

1. Cài ngrok từ [ngrok Windows download](https://ngrok.com/download/windows).
2. Tạo tài khoản miễn phí và thêm authtoken theo hướng dẫn của ngrok.
3. Chạy backend ở port `8080`.
4. Mở terminal khác:

```powershell
ngrok http 8080
```

5. Lấy URL HTTPS ngrok hiển thị và cấu hình:

```env
CLOUDINARY_EAGER_NOTIFICATION_URL=https://<ngrok-domain>/webhooks/cloudinary/eager
```

6. Restart backend và upload lại ảnh.

Ngrok thuận tiện khi cần kiểm tra raw body, Cloudinary headers và response của webhook. Free plan có quota; xem pricing hiện tại trước khi dùng lâu dài.

## 6. Workaround ngắn hạn khi chưa setup được tunnel

Nếu chưa thể cài hoặc chạy tunnel, có thể dùng một webhook collector public để nhận notification thật từ Cloudinary, sau đó replay request về backend local. Cách này không cần expose `localhost` ra Internet và không cần sửa code.

```text
Cloudinary → webhook collector public
                         ↓ copy raw body + headers
                    localhost:8080/webhooks/cloudinary/eager
```

### 6.1. Nhận notification tại webhook collector

Có thể dùng một dịch vụ request collector như [webhook.site](https://webhook.site/) cho môi trường development mà thôi.

1. Mở webhook.site và lấy URL riêng, ví dụ:

```text
https://webhook.site/<unique-id>
```

2. Đặt URL đó vào `.env`:

```env
CLOUDINARY_EAGER_NOTIFICATION_URL=https://webhook.site/<unique-id>
```

3. Restart backend để application đọc lại cấu hình.
4. Upload ảnh như bình thường.
5. Chờ Cloudinary gửi notification vào collector.
6. Lưu lại chính xác các giá trị sau:
   - raw request body;
   - `X-Cld-Signature`;
   - `X-Cld-Timestamp`.

Không dùng collector này cho production hoặc payload chứa dữ liệu nhạy cảm. Request body có thể chứa provider identifiers và URL media.

### 6.2. Replay notification về local

Dùng Postman hoặc HTTP client gọi:

```text
POST http://localhost:8080/webhooks/cloudinary/eager
```

Giữ nguyên hai header Cloudinary gửi:

```text
X-Cld-Signature: <signature-from-cloudinary>
X-Cld-Timestamp: <timestamp-from-cloudinary>
Content-Type: application/json
```

Body phải được gửi nguyên bản, không format lại hoặc thay đổi whitespace. Signature được tính trên raw body, timestamp và Cloudinary API secret nên chỉ một thay đổi nhỏ trong body cũng làm request bị `401`.

Replay trong khoảng thời gian `CLOUDINARY_WEBHOOK_TIMESTAMP_TOLERANCE` mặc định là hai giờ. Nếu local media tương ứng vẫn có `processingReference` khớp với `batch_id` trong payload, handler sẽ xử lý như notification thật và chuyển:

```text
PROCESSING → READY
```

hoặc:

```text
PROCESSING → FAILED
```

### 6.3. Giới hạn của workaround

Workaround này kiểm tra được payload thật, signature thật và logic cập nhật trạng thái, nhưng callback không tự động đi thẳng về backend local. Cloudinary đã gửi request thành công tới collector nên sẽ không tự gửi lại request đó cho local backend.

Nếu cần test đầy đủ flow tự động:

- Cloudinary gọi thẳng vào backend local thì phải dùng tunnel;
- hoặc deploy backend lên một staging/server public;
- không có cách để Cloudinary truy cập trực tiếp `localhost` từ Internet.

## 7. Các lỗi local thường gặp

### Application không start

Kiểm tra lỗi đầu tiên trong stack trace, không chỉ dòng `Application run failed` cuối cùng. Với Issue #68, các nguyên nhân thường gặp là:

- constructor của `CloudinaryNotificationSignatureVerifier` chưa được Spring autowire;
- `CLOUDINARY_EAGER_NOTIFICATION_URL` rỗng;
- URL dùng `http` thay vì `https`;
- thiếu `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` hoặc `CLOUDINARY_CLOUD_NAME`;
- thiếu `JWT_SECRET` hoặc các secret bắt buộc khác.

### Cloudinary không gọi được webhook

- tunnel process đã bị đóng;
- URL trong `.env` vẫn là URL cũ;
- backend không chạy ở port `8080`;
- URL thiếu `/webhooks/cloudinary/eager`;
- firewall hoặc mạng hiện tại chặn tunnel;
- tunnel trỏ tới sai port.

### Webhook trả `401`

- Cloudinary API secret trong backend không khớp Cloudinary account đang upload;
- body bị thay đổi trước khi signature được verify;
- timestamp vượt quá `CLOUDINARY_WEBHOOK_TIMESTAMP_TOLERANCE`;
- request không phải notification thật từ Cloudinary.

### Media vẫn ở `PROCESSING`

Kiểm tra lần lượt:

1. upload response có `batch_id` hay không;
2. `processingReference` trong database có đúng `batch_id` hay không;
3. tunnel có nhận request không;
4. webhook trả status gì;
5. log handler có tìm thấy media theo processing reference không.

## 8. Đưa lên production cần làm gì?

Production không nên phụ thuộc vào laptop, Quick Tunnel hoặc ngrok free. Cần deploy backend lên một môi trường có địa chỉ public ổn định, ví dụ VPS, cloud app service, container platform hoặc Kubernetes tùy hạ tầng của project.

### 7.1. Có domain HTTPS ổn định

Ví dụ backend production có domain:

```text
https://api.example.com
```

Cấu hình:

```env
CLOUDINARY_EAGER_NOTIFICATION_URL=https://api.example.com/webhooks/cloudinary/eager
```

Không dùng URL random của local tunnel trong production.

### 7.2. Reverse proxy phải forward đúng request

Nếu dùng Nginx, load balancer hoặc ingress, cần đảm bảo:

- `POST /webhooks/cloudinary/eager` được route tới backend;
- TLS certificate hợp lệ;
- raw request body không bị parse và serialize lại trước khi application verify signature;
- giữ nguyên `X-Cld-Signature` và `X-Cld-Timestamp`;
- không redirect webhook từ HTTPS sang HTTP;
- request body size đủ cho notification payload.

### 7.3. Security

Endpoint webhook cần public ở network layer để Cloudinary gọi được, nhưng application phải tiếp tục:

- chỉ permit `POST /webhooks/cloudinary/eager` không cần user JWT;
- verify Cloudinary signature trước khi parse và mutate state;
- reject signature thiếu/sai bằng `401`;
- không log API secret, signature secret hoặc full payload nếu payload có dữ liệu nhạy cảm;
- giới hạn/tối ưu request body và rate limit ở proxy nếu cần;
- không mở rộng `permitAll` cho các endpoint khác.

CORS vẫn chỉ cấu hình cho các origin của frontend production, ví dụ:

```env
CORS_ALLOWED_ORIGINS=https://shop.example.com,https://admin.example.com
```

Không cần thêm `https://api.example.com/webhooks/cloudinary/eager` vào CORS.

### 7.4. Secret và configuration

Không commit giá trị thật vào repository. Đưa các giá trị sau vào secret manager hoặc environment variables của môi trường deploy:

```env
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_EAGER_NOTIFICATION_URL=https://api.example.com/webhooks/cloudinary/eager
CLOUDINARY_WEBHOOK_TIMESTAMP_TOLERANCE=2h
```

Các secret khác như database password, JWT secret, OAuth secret và payment secret cũng phải được quản lý tương tự.

### 7.5. Database migration và deploy order

Trước khi bật traffic production:

1. Backup database.
2. Chạy Liquibase migration `changeset-26.sql`.
3. Kiểm tra các cột processing status/reference đã tồn tại.
4. Deploy backend với configuration production.
5. Kiểm tra health/readiness của application.
6. Gửi một upload smoke test.
7. Xác nhận Cloudinary gọi được webhook và media chuyển `PROCESSING → READY` hoặc `FAILED`.

Không nên nhận upload từ phiên bản application mới trước khi schema migration hoàn tất.

### 7.6. Observability và vận hành

Nên có:

- log correlation id cho upload và webhook;
- log media id, processing reference, outcome và HTTP result, không log secret;
- metric cho webhook `2xx`, `4xx`, `5xx`;
- cảnh báo media bị `PROCESSING` quá lâu;
- theo dõi Cloudinary notification retry và failure reason;
- dashboard cho upload failure và orphan cleanup.

Implementation hiện tại trả `503` khi webhook đến trước transaction insert để tận dụng retry của Cloudinary. Tuy nhiên, nếu tất cả retry đều hết mà media vẫn `PROCESSING`, hệ thống hiện chưa có reconciliation job. Đây là follow-up vận hành nên cân nhắc nếu production có traffic đáng kể.

## 9. Checklist nhanh

### Local

- [ ] Backend start thành công ở port `8080`.
- [ ] `cloudflared --version` chạy được.
- [ ] Quick Tunnel đang chạy.
- [ ] `CLOUDINARY_EAGER_NOTIFICATION_URL` dùng HTTPS và đúng path.
- [ ] Backend đã restart sau khi đổi `.env`.
- [ ] Upload response có `batch_id`/processing reference.
- [ ] Tunnel nhận `POST /webhooks/cloudinary/eager`.
- [ ] Signature hợp lệ.
- [ ] Media chuyển sang `READY` hoặc `FAILED`.

### Production

- [ ] Backend có domain HTTPS ổn định.
- [ ] DNS, TLS certificate và reverse proxy hoạt động.
- [ ] Webhook path được route chính xác.
- [ ] Raw body và Cloudinary signature headers được giữ nguyên.
- [ ] Secret nằm trong secret manager/environment, không nằm trong Git.
- [ ] Liquibase migration chạy thành công.
- [ ] Security chỉ permit đúng webhook `POST`.
- [ ] CORS chỉ chứa origin frontend.
- [ ] Có log, metric và cảnh báo processing stuck.
- [ ] Đã chạy production smoke test.

## Tài liệu tham khảo

- [Cloudinary notifications](https://cloudinary.com/documentation/notifications)
- [Cloudflare Quick Tunnels](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/)
- [Cloudflare Tunnel downloads](https://developers.cloudflare.com/tunnel/downloads/)
- [ngrok pricing](https://ngrok.com/pricing)
- [ngrok share localhost](https://ngrok.com/use-cases/share-localhost)
