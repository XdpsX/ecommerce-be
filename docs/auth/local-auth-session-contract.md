# Phiên đăng nhập LOCAL

Áp dụng cho email/password. Google OAuth vẫn dùng redirect/access token, chưa tạo refresh session/cookie.

| API | Kết quả |
| --- | --- |
| `POST /auth/register`, `/auth/login` | JSON `{ "accessToken": "..." }` và refresh cookie; mỗi lần tạo một session độc lập |
| `POST /auth/refresh` | Trả access token mới, rotate refresh credential đúng một lần |
| `POST /auth/logout` | Revoke session của cookie hiện tại, trả 204 và xóa cookie |

## Defaults và bảo mật

- Access token 15 phút; refresh session hết hạn tuyệt đối sau 30 ngày. Rotate không kéo dài session.
- Cookie `refresh_session`: host-only, HttpOnly, path `/auth`, SameSite=Strict; Secure bật ngoài dev.
- Không trả refresh credential trong JSON, lưu plaintext, log hoặc đưa vào error.
- Credential malformed/unknown/expired/revoked/reused/sai secret đều trả `401 INVALID_REFRESH_CREDENTIAL`.
- Reuse credential cũ revoke session sau khi transaction có row lock hoàn tất.
- Logout không blacklist access token đã cấp; token đó còn dùng được tới expiry.

## Gọi từ browser

Refresh/logout cần cookie qua `credentials: "include"` và header `X-Session-Request: 1`.
Guard header phục vụ CSRF boundary, không thay authentication. Credentialed CORS giới hạn bởi `app.cors.allowed-origins`.

Deployment thông thường same-site dùng SameSite=Strict. Nếu frontend cross-site, cấu hình SameSite=None,
Secure=true và HTTPS. Các giá trị cookie, issuer, lifetime và guard header nằm trong `app.auth-session.*`.
