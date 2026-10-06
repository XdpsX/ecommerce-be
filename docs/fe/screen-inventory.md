# Danh mục màn hình FE single-store

Tài liệu này là **danh sách màn hình cần thiết kế** cho M1, không phải bản thiết kế giao diện hay API contract. [FE handoff](./admin-first-api-and-screen-design.md) giữ bản đồ API và quy tắc tích hợp; [Backoffice brief](./backoffice-screen-brief.md) và [Storefront brief](./storefront-screen-brief.md) mô tả mục đích, tác vụ và trạng thái của từng màn để gửi AI thiết kế Figma.

Phạm vi: một cửa hàng, `ADMIN` vận hành và `USER` mua hàng. Dùng tiếng Việt và VND. Ưu tiên Backoffice trước, rồi Storefront. Mã A0–A8 và S0–S8 giữ theo FE handoff cũ; hậu tố `a/b` tách các màn trước đây bị gộp. `A9` và `A10` bổ sung hai màn chưa có mã.

**Cách hiểu “màn hình”:** một frame trang độc lập hoặc một bề mặt lớn cần AI thiết kế riêng. Drawer/modal nhỏ được ghi dưới màn cha, không tạo file riêng. Route FE trong brief chỉ là gợi ý điều hướng, không phải route backend đã tồn tại.

## Backoffice — 11 màn/bề mặt

| ID | Module | Màn hình | Kiểu | Mục đích chính | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| A0 | Truy cập | Đăng nhập admin | Form / access gate | Xác thực và chặn tài khoản không có role `ADMIN` | Không có đăng ký admin công khai. |
| A10 | Điều hướng | Trang bắt đầu Backoffice | Trang điều hướng | Đưa admin đến các tác vụ chính | Không có KPI/chart vì chưa có API thống kê. |
| A1 | Catalog | Danh mục | Cây + danh sách + editor | Tạo/sửa, di chuyển, sắp xếp và xóa category | Form và xác nhận là trạng thái của A1. |
| A2 | Catalog | Thương hiệu | Danh sách + editor | Quản lý brand và category liên kết | Form và xác nhận là trạng thái của A2. |
| A3 | Catalog | Thuộc tính SKU | Danh sách + chi tiết | Quản lý option và value dùng tạo SKU | Không có màn quản lý thuộc tính mô tả riêng. |
| A4 | Sản phẩm | Danh sách sản phẩm | Danh sách | Tìm, xem giá/tồn tổng hợp, mở editor và công khai | Trạng thái công khai khác với trạng thái SKU. |
| A5 | Sản phẩm | Tạo/sửa sản phẩm | Editor nhiều phần | Sửa metadata, ảnh, SKU, giá và sale | Khi tạo mới, phần SKU chỉ dùng sau khi đã có Product ID. |
| A6 | Tồn kho | Tồn kho một SKU | Panel + form điều chỉnh | Xem balance và điều chỉnh có lý do | Bề mặt mở từ A5; chưa có lịch sử adjustment để liệt kê. |
| A7 | Đơn hàng | Danh sách đơn | Danh sách / tra cứu | Lọc trạng thái, tìm chính xác mã đơn và mở chi tiết | Không có tìm theo tên khách/ngày. |
| A8 | Đơn hàng | Chi tiết đơn | Chi tiết / tác vụ | Xem snapshot và xử lý trạng thái, hủy, refund | Refund có thể mở chi tiết từ đây. |
| A9 | Hoàn tiền | Hàng đợi refund | Hàng đợi / tác vụ | Tìm các refund thủ công theo trạng thái, mở đơn và xử lý | Có API danh sách; nên là mục điều hướng riêng. |

## Storefront — 12 màn/bề mặt

| ID | Module | Màn hình | Kiểu | Mục đích chính | Ghi chú |
| --- | --- | --- | --- | --- | --- |
| S0 | Khám phá | Trang chủ | Trang giới thiệu catalog | Điều hướng vào danh mục và sản phẩm mới | Dùng dữ liệu catalog thật; nội dung chiến dịch là phần sau. |
| S1 | Khám phá | Danh sách sản phẩm (PLP) | Danh sách / bộ lọc | Tìm và thu hẹp sản phẩm theo catalog/SKU | Trang category có thể là biến thể của S1. |
| S2 | Khám phá | Chi tiết sản phẩm (PDP) | Chi tiết / chọn SKU | Chọn tổ hợp option, xem giá/tồn SKU và thêm giỏ | Chỉ SKU hợp lệ mới thêm giỏ được. |
| S3a | Tài khoản | Đăng nhập | Form | Vào phiên mua hàng và tiếp tục tác vụ trước đó | Có thể gộp cùng một frame với S3b dưới dạng hai trạng thái. |
| S3b | Tài khoản | Đăng ký | Form | Tạo tài khoản `USER` | Không có đăng ký admin. |
| S4a | Tài khoản | Hồ sơ của tôi | Chi tiết / form | Xem tài khoản và đổi tên | Email, role, avatar chỉ đọc theo API hiện tại. |
| S4b | Tài khoản | Sổ địa chỉ | Danh sách + form | Thêm/sửa/xóa địa chỉ nhận hàng | Không thiết kế chọn địa chỉ mặc định. |
| S5 | Giỏ hàng | Giỏ hàng | Danh sách / chỉnh số lượng | Kiểm tra SKU, số lượng, giá ước tính và vào checkout | Dùng được cho guest và customer. |
| S6 | Mua hàng | Checkout | Form / xác nhận | Chọn địa chỉ, kiểm tra lại đơn và gửi checkout | Phải đăng nhập; giá cuối do server chốt. |
| S7 | Thanh toán | Chuyển hướng / kiểm tra trạng thái thanh toán | Trạng thái tiến trình | Đi đến VNPay và sau đó tra trạng thái đơn | Không coi browser return là xác nhận đã thanh toán. |
| S8a | Đơn của tôi | Danh sách đơn | Danh sách | Theo dõi các đơn của chính mình | Không có tìm/lọc đơn customer theo API hiện tại. |
| S8b | Đơn của tôi | Chi tiết đơn | Chi tiết / tác vụ | Xem snapshot, retry payment hoặc hủy khi hợp lệ | Mở bằng tracking number, không bằng admin order ID. |

## Kích thước viewport và quyết định có vẽ frame riêng

Các số dưới đây là **chiều rộng viewport theo CSS px**, không phải danh sách thiết bị hoặc breakpoint FE đã chốt. Chiều cao frame có thể chọn đủ để trình bày phần đầu trang; trang dài được cuộn. Khi hiện thực, chọn breakpoint theo lúc nội dung bắt đầu chật/vỡ thay vì khóa layout vào đúng các số này ([web.dev](https://web.dev/articles/responsive-web-design-basics), [MDN](https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/CSS_layout/Media_queries)).

| Nhóm | Rộng | Mức đề xuất | Có cần frame Figma riêng? | Mục đích |
| --- | ---: | --- | --- | --- |
| Backoffice | 1440 | **Mặc định vẽ** | Có, cho các màn A0–A10 | Bản desktop gốc để chốt cấu trúc vận hành. |
| Backoffice | 1280 | **Kiểm tra responsive** | Chỉ khi bố cục khác 1440 | Thử bảng/filter/editor dày ở A1, A4, A5, A7, A8, A9 trên laptop hẹp. |
| Backoffice | 768 | **Cần hỏi trước khi vẽ** | Nếu admin dùng tablet, vẽ vài màn đại diện trước | Quyết định cách đổi sidebar, bảng và tác vụ trên tablet; không mặc định nhân đôi mọi frame. |
| Backoffice | 390 | **Cần hỏi phạm vi hỗ trợ** | Không vẽ toàn bộ trong vòng đầu | Nếu cần xử lý đơn trên điện thoại, phải chọn các tác vụ ưu tiên và thiết kế luồng riêng. |
| Storefront | 390 | **Mặc định vẽ** | Có, cho các màn S0–S8b | Bản mobile gốc cho hành trình mua hàng. |
| Storefront | 1440 | **Mặc định vẽ** | Có, cho các màn S0–S8b | Bản desktop gốc cho catalog và checkout. |
| Storefront | 360 | **Kiểm tra responsive** | Chỉ khi phát hiện thay đổi cấu trúc | Bắt lỗi tràn chữ/control ở điện thoại hẹp, nhất là S1, S2, S5, S6, S8b. |
| Storefront | 768 | **Cần hỏi trước khi vẽ** | Nếu cần tablet, vẽ S1, S2, S5, S6 trước | Chốt cách chuyển giữa bố cục mobile và desktop ở các màn mua hàng. |
| Cả hai | 1920 | **Kiểm tra responsive** | Thường không cần | Kiểm tra giới hạn độ rộng nội dung và khoảng trống trên màn lớn. |

**Cần nhắc khi giao việc vẽ Figma:** (1) Backoffice có cần dùng trên tablet 768 không? (2) Backoffice có cần thao tác trên mobile 390 không, và tác vụ nào? (3) Storefront có cần frame tablet 768 riêng không? Nếu chưa chốt, vòng đầu chỉ yêu cầu frame Backoffice 1440 và Storefront 390 + 1440; các kích thước còn lại là điểm kiểm tra, không phải số frame phải vẽ.

## Luồng và mức ưu tiên thiết kế

1. **Backoffice vận hành:** A0 → A10 → A7 → A8 ↔ A9. Đây là đường xử lý đơn/refund; A9 có thể mở trực tiếp từ menu và A8.
2. **Backoffice catalog:** A4 → A5 → A6; A1, A2, A3 cung cấp dữ liệu để tạo sản phẩm/SKU. Có thể thiết kế A1–A3 trước A5 nếu AI cần biết form chọn dữ liệu.
3. **Storefront mua hàng:** S0/S1 → S2 → S5 → S3a/S3b (khi cần) → S6 → S7 → S8b; S8a là lối vào các đơn đã tạo. S4b phục vụ chọn địa chỉ trong S6.

Trong M1, sale theo SKU là phần của A5, upload ảnh là thao tác của A1/A2/A5, hủy đơn là dialog của A8/S8b, và xử lý refund là dialog/panel của A8/A9. Không cần frame trang riêng cho các tác vụ này.

## Các trạng thái chung cần bàn giao cho AI Figma

- Mỗi danh sách: đang tải, có dữ liệu, trống, lỗi tải lại; có phân trang **chỉ khi API tương ứng có phân trang**.
- Mỗi form: mặc định, đang gửi, lỗi validation, hoàn tất; hành động xóa/hủy/đổi trạng thái có bước xác nhận và thông báo kết quả.
- Auth: guest, `USER`, `ADMIN`, hết phiên và 403. Không để `USER` đi vào Backoffice.
- Order/payment/refund: hiển thị từng trạng thái riêng, tránh gộp “đã đặt hàng” với “đã trả tiền”.
- `409` do version cũ trên Category/Brand: tải lại dữ liệu và cho admin kiểm tra trước khi gửi lại.

## Ngoài phạm vi thiết kế như tính năng đã chạy

Dashboard KPI/chart, báo cáo, quản lý khách hàng trong Backoffice, payment attempt list, lịch sử điều chỉnh tồn kho, shipping carrier/return/RTO, voucher engine, refund tự động, partial refund, multi-vendor và social login chưa chốt session. Nếu muốn minh họa future state, đánh dấu rõ là ý tưởng, không đưa vào luồng chính.

Trước khi triển khai FE, đối chiếu schema trên `/v3/api-docs` khi backend chạy; [BE readiness](./backend-readiness-before-frontend.md) ghi giới hạn kiểm chứng hiện tại.
