# Brief màn hình Storefront cho AI Figma

Đọc [danh mục màn hình](./screen-inventory.md) để thấy toàn bộ hành trình, gồm [ma trận kích thước cần vẽ](./screen-inventory.md#kích-thước-viewport-và-quyết-định-có-vẽ-frame-riêng), và [FE handoff](./admin-first-api-and-screen-design.md) để tra API. File này mô tả **loại màn, công việc và trạng thái**, chưa quyết định layout chi tiết. Mặc định vẽ desktop 1440px và mobile 390px; tablet 768px cần chốt trước khi vẽ. Nội dung tiếng Việt, tiền VND. Dùng cùng ngôn ngữ thiết kế với Backoffice nhưng ưu tiên trải nghiệm mua hàng.

## Khám phá catalog

### S0 — Trang chủ

- **Kiểu/mục tiêu:** trang vào cửa hàng; đưa khách đến category và sản phẩm mới.
- **Cần thể hiện:** category đang hiển thị, sản phẩm mới, đường tới PLP/PDP/giỏ hàng. Nội dung biên tập hoặc chiến dịch chỉ là placeholder nếu chưa có nguồn dữ liệu.
- **Trạng thái chính:** loading/empty/error cho các khối lấy từ API, guest/đã đăng nhập.
- **Nguồn BE:** `GET /categories` hoặc `/categories/tree`, `GET /products/latest`.

### S1 — Danh sách sản phẩm (PLP)

- **Kiểu/mục tiêu:** trang tìm kiếm và lọc Product; category view là biến thể có filter category sẵn.
- **Cần thể hiện:** search, category, brand, khoảng giá, còn/hết hàng, option value, sort và phân trang; mỗi card dùng ảnh, tên, min/max price và availability tổng Product.
- **Trạng thái chính:** chưa lọc, đang lọc, không có kết quả, lỗi tải, trang kết quả; cho thấy filter đang áp dụng để khách có thể bỏ.
- **Nguồn BE:** `GET /products`, `GET /products/filter-options`, `GET /categories/tree`, `GET /brands?categoryId=`, `GET /categories/{slug}` khi vào qua category URL. Option/price/stock filter được BE áp trên cùng SKU; price range và `inStock` trên card vẫn tổng hợp toàn Product, không đại diện riêng cho SKU đang lọc.

### S2 — Chi tiết sản phẩm (PDP)

- **Kiểu/mục tiêu:** trang chi tiết giúp khách chọn đúng Variant/SKU trước khi thêm giỏ.
- **Cần thể hiện:** ảnh, tên, mô tả, category/brand, nhóm option và value, giá cuối và availability của SKU được chọn, số lượng và CTA thêm giỏ. Chưa chọn đủ tổ hợp thì chưa giả định có SKU.
- **Trạng thái chính:** chưa chọn SKU, tổ hợp hợp lệ còn hàng, hợp lệ hết hàng, tổ hợp không tồn tại, thêm giỏ thành công/lỗi, sản phẩm không còn hiển thị hoặc slug không tồn tại.
- **Nguồn BE:** `GET /products/slug/{slug}`, `POST /cart/items`. Dùng `variantId` của tổ hợp đã chọn; guest mutation cần cookie và `X-Cart-Request: 1`.

## Tài khoản và địa chỉ

### S3a — Đăng nhập

- **Kiểu/mục tiêu:** form vào phiên `USER`, có đường đến đăng ký và quay lại tác vụ mua hàng trước đó.
- **Cần thể hiện:** email, mật khẩu, trạng thái submit/lỗi; sau login có thể claim guest cart và xử lý chênh lệch giỏ. Không mặc định social login hoạt động.
- **Trạng thái chính:** mặc định, loading, sai thông tin, thành công, claim cart thành công/lỗi.
- **Nguồn BE:** `POST /auth/login`, `GET /users/me`, `POST /cart/claim`, `POST /auth/refresh/logout` cho lifecycle phiên.

### S3b — Đăng ký

- **Kiểu/mục tiêu:** form tạo tài khoản mua hàng; có thể là biến thể của frame S3a.
- **Cần thể hiện:** tên, email, mật khẩu; kết quả tạo tài khoản và đường tiếp tục checkout/giỏ nếu đến từ luồng mua.
- **Trạng thái chính:** validation, email đã dùng, đang gửi, thành công.
- **Nguồn BE:** `POST /auth/register` tạo `USER` và trả session. Không có bước xác minh email trong flow hiện tại.

### S4a — Hồ sơ của tôi

- **Kiểu/mục tiêu:** xem thông tin tài khoản và sửa tên.
- **Cần thể hiện:** tên chỉnh sửa được; email, avatar và role hiển thị theo profile nhưng không có control chỉnh sửa từ API hiện tại.
- **Trạng thái chính:** loading, đang lưu, validation, lưu thành công/lỗi, hết phiên.
- **Nguồn BE:** `GET /users/me`, `PATCH /users/me` (chỉ `name`). Không thiết kế đổi mật khẩu/email trong M1 hiện tại.

### S4b — Sổ địa chỉ

- **Kiểu/mục tiêu:** quản lý các địa chỉ giao hàng của chính customer; checkout có thể mở/chọn từ đây.
- **Cần thể hiện:** danh sách địa chỉ, tạo/sửa/xóa; form có người nhận, điện thoại, địa chỉ, phường/xã, quận/huyện, tỉnh/thành, mã bưu chính tùy chọn.
- **Trạng thái chính:** chưa có địa chỉ, form tạo/sửa, validation, xác nhận xóa, lỗi/hết phiên.
- **Nguồn BE:** `GET/POST /users/me/addresses`, `PUT/DELETE /users/me/addresses/{addressId}`. Không có cờ địa chỉ mặc định.

## Giỏ hàng, checkout và thanh toán

### S5 — Giỏ hàng

- **Kiểu/mục tiêu:** kiểm tra các SKU định mua và chỉnh số lượng trước checkout.
- **Cần thể hiện:** sản phẩm/SKU, option đã chọn, số lượng, giá và subtotal **ước tính**, availability `AVAILABLE/INSUFFICIENT_STOCK/UNAVAILABLE`, xóa item và CTA checkout. Nếu guest đăng nhập, thể hiện kết quả claim guest cart.
- **Trạng thái chính:** giỏ trống, đang cập nhật, SKU hết hàng/không bán, lỗi cập nhật, chênh lệch sau đăng nhập.
- **Nguồn BE:** `GET /cart`, `POST /cart/items`, `PUT/DELETE /cart/items/{variantId}`, `POST /cart/claim`. Giỏ không reserve inventory.

### S6 — Checkout

- **Kiểu/mục tiêu:** xác nhận thông tin giao hàng và gửi một đơn hàng từ giỏ của `USER`.
- **Cần thể hiện:** chọn/thêm địa chỉ, tóm tắt item và giá ước tính, thông báo server sẽ tính lại giá/tồn, nút đặt hàng có trạng thái đang gửi; không submit lặp với key mới khi request chưa rõ kết quả.
- **Trạng thái chính:** chưa đăng nhập, chưa có địa chỉ, giỏ không hợp lệ, đang gửi, Order đã tạo cùng payment URL, Order đã tạo nhưng `payment: null`, lỗi trước khi tạo Order, lỗi sau commit có `orderId` để tra cứu.
- **Nguồn BE:** `GET /cart`, `GET/POST /users/me/addresses`, `POST /checkout` với `Idempotency-Key` và `{addressId,description?}`. Retry cùng key khi payment initialization lỗi retryable; Order snapshot là giá đã chốt.

### S7 — Chuyển hướng / kiểm tra trạng thái thanh toán

- **Kiểu/mục tiêu:** trung gian giữa checkout, VNPay và trang Order; giải thích trạng thái sau khi khách quay lại.
- **Cần thể hiện:** chuyển đến `payment.vnpUrl` khi có URL; khi quay lại hoặc URL khởi tạo lỗi, cho mở Order và thử tạo payment attempt mới nếu Order vẫn `PENDING_PAYMENT`. Trạng thái thanh toán lấy từ Order sau khi BE nhận callback, không dựa vào URL/browser return.
- **Trạng thái chính:** đang chuyển hướng, chưa có URL, payment vẫn `PENDING`, đã `PAID`, đã hết hạn/hủy, không tải được Order và thử lại. Không tự hiển thị “thành công” chỉ vì khách quay về.
- **Nguồn BE:** `payment.vnpUrl` từ `POST /checkout` hoặc `POST /orders/{orderId}/payment-attempts`; `GET /orders/code/{trackingNumber}` để tra trạng thái. `/payments/vnpay_ipn` dành cho VNPay server, browser không gọi.

## Đơn của tôi

### S8a — Danh sách đơn

- **Kiểu/mục tiêu:** xem các Order của customer hiện tại và mở chi tiết.
- **Cần thể hiện:** tracking number, ngày tạo, tổng tiền/currency, order status, payment status, refund summary nếu có; phân trang.
- **Trạng thái chính:** chưa có đơn, loading, lỗi tải, hết phiên.
- **Nguồn BE:** `GET /orders/me`. API hiện tại không có filter/tìm kiếm customer order.

### S8b — Chi tiết đơn

- **Kiểu/mục tiêu:** theo dõi một Order đã tạo, gồm thanh toán, hủy và refund status nếu phát sinh.
- **Cần thể hiện:** tracking, địa chỉ giao hàng và item/giá **snapshot**, order/payment status, lý do hủy, refund summary; retry payment khi Order còn `PENDING_PAYMENT`; yêu cầu hủy có lý do khi `PENDING_PAYMENT` hoặc `CONFIRMED`.
- **Trạng thái chính:** đang tải/404/không thuộc chủ tài khoản, chờ thanh toán, đã xác nhận, đang xử lý, giao hàng, đã hủy/hết hạn; retry/hủy thành công hoặc bị từ chối do trạng thái đổi.
- **Nguồn BE:** `GET /orders/code/{trackingNumber}`, `POST /orders/{orderId}/payment-attempts`, `POST /orders/{orderId}/cancellation`. Customer không xử lý refund thủ công; chỉ xem summary trong Order.

## Prompt gửi AI Figma cho nhóm Storefront

> Dùng file này và `screen-inventory.md` để tạo các frame S0, S1, S2, S3a/S3b, S4a/S4b, S5, S6, S7, S8a/S8b. Ưu tiên hành trình PLP → PDP → Cart → Checkout → trạng thái thanh toán → chi tiết đơn. Mỗi frame ghi ID, mục tiêu, CTA, dữ liệu chính và trạng thái rỗng/lỗi/đang tải. Dùng tiếng Việt, VND, desktop 1440px và mobile 390px; chỉ thêm tablet 768px sau khi chốt nhu cầu theo ma trận kích thước trong `screen-inventory.md`. Giữ sự khác nhau giữa giá ước tính ở giỏ, giá chốt trong Order, order status và payment status. Không tạo voucher engine, social login chưa chốt, hay xác nhận thanh toán từ browser return.
