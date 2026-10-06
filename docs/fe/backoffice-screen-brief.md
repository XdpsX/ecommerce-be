# Brief màn hình Backoffice cho AI Figma

Đọc [danh mục màn hình](./screen-inventory.md) để thấy toàn bộ hành trình, gồm [ma trận kích thước cần vẽ](./screen-inventory.md#kích-thước-viewport-và-quyết-định-có-vẽ-frame-riêng), và [FE handoff](./admin-first-api-and-screen-design.md) để tra API. File này mô tả **loại màn, công việc và trạng thái**, chưa quyết định grid, màu sắc, typography hay vị trí control chi tiết. Mặc định vẽ desktop 1440px; tablet 768px cần chốt phạm vi trước khi vẽ. Nội dung tiếng Việt, tiền VND. Một design system chung cho bảng, form, trạng thái và dialog.

## Truy cập và điều hướng

### A0 — Đăng nhập admin

- **Kiểu/mục tiêu:** form xác thực; chỉ tài khoản `ADMIN` vào Backoffice.
- **Cần thể hiện:** email, mật khẩu, submit, lỗi sai thông tin hoặc hết phiên, trạng thái tài khoản `USER` không đủ quyền. Sau login/refresh, đọc profile để quyết định điều hướng.
- **Trạng thái chính:** form ban đầu, đang gửi, lỗi đăng nhập, 403, đăng nhập thành công. Không có form đăng ký admin.
- **Nguồn BE:** `POST /auth/login`, `POST /auth/refresh`, `GET /users/me`, `POST /auth/logout`.

### A10 — Trang bắt đầu Backoffice

- **Kiểu/mục tiêu:** trang điều hướng sau login; cho admin thấy lối vào Đơn hàng, Hoàn tiền, Sản phẩm, Danh mục, Thương hiệu và Thuộc tính SKU.
- **Cần thể hiện:** các tác vụ thường dùng và đường vào từng module; không đặt KPI/chart hay số liệu kinh doanh giả.
- **Trạng thái chính:** có quyền `ADMIN`, lỗi/hết phiên; không cần loading dữ liệu nếu chỉ là điều hướng.
- **Nguồn BE:** profile từ `GET /users/me`; không có API dashboard.

## Catalog và hàng hóa

### A1 — Danh mục

- **Kiểu/mục tiêu:** bề mặt quản lý taxonomy dạng cây và danh sách; tạo/sửa, đổi parent/thứ tự, xóa category hợp lệ.
- **Cần thể hiện:** tên, slug, cấp/cha, status, `effectivelyActive`, thứ tự, ảnh; filter tên/status/parent/level, editor tạo/sửa, thao tác move/reorder. Cho thấy một node inactive có thể khiến hậu duệ không xuất hiện trên storefront.
- **Trạng thái chính:** danh sách rỗng/lỗi, form tạo/sửa, đang upload ảnh, xác nhận xóa, lỗi validation, `409` version cũ và tải lại. Sau move/reorder cần làm mới dữ liệu trước khi sửa/xóa tiếp.
- **Nguồn BE:** `/admin/categories/**`, `POST /media/image-upload?resource=category`. Update/delete dùng `version` từ lần đọc mới nhất; không có cascading status trong DB.

### A2 — Thương hiệu

- **Kiểu/mục tiêu:** danh sách và editor brand; quản lý trạng thái, logo và category liên kết.
- **Cần thể hiện:** tên, trạng thái, logo, category IDs/nhãn liên kết; tìm theo tên, lọc trạng thái, tạo/sửa/xóa.
- **Trạng thái chính:** loading/empty/error, editor mới/cũ, lỗi tên trùng, xác nhận xóa, `409` version cũ và refetch.
- **Nguồn BE:** `/admin/brands/**`, `GET /admin/categories`, `POST /media/image-upload?resource=brand`. Update/delete gửi `version`.

### A3 — Thuộc tính SKU

- **Kiểu/mục tiêu:** danh sách option và chi tiết value bên trong; chuẩn bị các tổ hợp dùng khi tạo Variant.
- **Cần thể hiện:** code, tên, status, display order của option/value; tạo/sửa option, thêm/sửa value, sắp xếp lại value. Code là định danh nhập khi tạo, không phải trường sửa tùy ý.
- **Trạng thái chính:** chưa có option/value, form và validation, xác nhận khi tắt item đang dùng, lỗi thao tác.
- **Nguồn BE:** `/admin/variant-options/**`. Không có delete option/value trong API hiện tại.

### A4 — Danh sách sản phẩm

- **Kiểu/mục tiêu:** danh sách Product để tìm, xem khả năng bán tổng quan và mở editor.
- **Cần thể hiện:** ảnh đại diện, tên, category, brand, publication, min/max effective price, tổng `onHand/reserved/available` trên Variant ACTIVE. Tìm kiếm, lọc `hasPublished`, sort, phân trang, tạo mới, publish/unpublish có xác nhận.
- **Trạng thái chính:** loading/empty/error, không có SKU ACTIVE (giá null, tồn 0), đang đổi publication, lỗi thao tác.
- **Nguồn BE:** `GET /admin/products`, `PATCH /admin/products/{id}/publication`. Tổng tồn ở Product không thay thế tồn từng SKU.

### A5 — Tạo/sửa sản phẩm

- **Kiểu/mục tiêu:** editor cho metadata Product và các SKU/giá. Có trạng thái tạo mới và chỉnh sửa sau khi Product tồn tại.
- **Cần thể hiện:** tên, slug, mô tả, category, brand, tối đa 5 ảnh; kiểm tra slug. Sau khi lưu Product, quản lý Variant với SKU, tổ hợp option value, barcode, base price, status, lịch sale và giá cuối. Thể hiện publication của Product tách khỏi status của Variant; xóa Product có xác nhận khi policy BE cho phép.
- **Trạng thái chính:** tạo mới chưa có SKU, đã lưu và có/không có Variant, slug không hợp lệ/trùng, SKU trùng hoặc tổ hợp trùng, lưu thất bại, upload lỗi, sale chưa/đang/hết hiệu lực theo dữ liệu BE.
- **Nguồn BE:** `/admin/products/**`, `/admin/products/{productId}/variants/**`, `/admin/variant-options`, `POST /media/image-upload?resource=product`. Không thiết kế rule engine khuyến mại.

### A6 — Tồn kho một SKU

- **Kiểu/mục tiêu:** panel/bề mặt mở từ A5 để xem balance và điều chỉnh tồn của một Variant cụ thể.
- **Cần thể hiện:** SKU, `onHand`, `reserved`, `available`; nhập `quantityDelta` có dấu và `reason` bắt buộc; cho biết kết quả sau khi ghi và tải lại balance.
- **Trạng thái chính:** chưa có balance, điều chỉnh hợp lệ, delta bằng 0/thiếu lý do, lỗi do không đủ tồn khả dụng, đang gửi và cập nhật thành công.
- **Nguồn BE:** `GET /admin/inventory/variants/{variantId}`, `POST /admin/inventory/variants/{variantId}/adjustments`. Chưa có read API lịch sử adjustment.

## Vận hành đơn và hoàn tiền

### A7 — Danh sách đơn

- **Kiểu/mục tiêu:** danh sách tác vụ để tìm và mở một Order.
- **Cần thể hiện:** tracking number, ngày tạo, tổng tiền/currency, order status, payment status và refund summary nếu có; lọc theo hai status, tìm **chính xác** tracking number, phân trang.
- **Trạng thái chính:** loading/empty/error, bộ lọc không có kết quả, lỗi tham số trang, đã chọn một đơn.
- **Nguồn BE:** admin `GET /orders`; `pageSize` 1–20, sort mặc định mới nhất trước. Không có filter theo khách hàng/ngày.

### A8 — Chi tiết đơn

- **Kiểu/mục tiêu:** trang chi tiết và tác vụ vận hành trên một Order.
- **Cần thể hiện:** tracking, order status, payment status, thông tin khách, shipping snapshot, item snapshot (SKU, số lượng, giá tại lúc mua), tổng tiền và refund summary. Cho phép bước fulfillment kế tiếp, hủy với lý do khi hợp lệ, mở refund nếu `refund.id` tồn tại.
- **Trạng thái chính:** đang tải/404/lỗi, mỗi trạng thái Order liên quan, dialog xác nhận chuyển bước, dialog hủy có reason, refund `PENDING/SUCCEEDED/FAILED`, lỗi transition do dữ liệu đã thay đổi; refetch sau thao tác.
- **Nguồn BE:** admin `GET /orders/{id}`, `PATCH /orders/{id}/status`, `POST /admin/orders/{id}/cancellation`, `GET /admin/refunds/{refundId}` và mutation refund. Fulfillment chỉ `CONFIRMED → PROCESSING → SHIPPED → DELIVERED`; admin hủy trước `SHIPPED` theo policy BE.

### A9 — Hàng đợi refund

- **Kiểu/mục tiêu:** danh sách công việc hoàn tiền thủ công để admin xử lý theo thứ tự yêu cầu.
- **Cần thể hiện:** tracking number, trạng thái, số tiền/currency, reason, requestedAt; lọc trạng thái, phân trang, mở Order hoặc chi tiết refund. Với refund `PENDING`, có thao tác complete/fail; với `FAILED`, có retry. Cần ghi rõ đây là bước admin xử lý thủ công.
- **Trạng thái chính:** loading/empty/error, không có refund theo filter, dialog complete (external reference tùy chọn), dialog fail (failure reason), retry, lỗi mutation và refetch.
- **Nguồn BE:** `GET /admin/refunds`, `GET /admin/refunds/{refundId}`, `POST /admin/refunds/{refundId}/complete|fail|retry`; hàng đợi cũ nhất trước. Không có tích hợp hoàn tiền tự động qua VNPay.

## Prompt gửi AI Figma cho nhóm Backoffice

> Dùng file này và `screen-inventory.md` để tạo các frame A0, A10, A1–A9 cho Backoffice single-store. Mỗi frame ghi ID, mục tiêu, tác vụ chính, trạng thái trống/lỗi/đang tải và các dialog quan trọng. Ưu tiên A0 → A10 → A7/A8/A9, rồi A4/A5/A6 và A1/A2/A3. Dùng tiếng Việt, VND và desktop 1440px. Chỉ vẽ biến thể tablet 768px sau khi đã chốt nhu cầu theo ma trận kích thước trong `screen-inventory.md`. Thiết kế ở mức concept/structure; không bịa KPI, quản lý khách hàng, hãng vận chuyển, hoàn tiền tự động hoặc dữ liệu API chưa có.
