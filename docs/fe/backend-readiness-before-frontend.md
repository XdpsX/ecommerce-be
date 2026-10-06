# BE cần hoàn thiện trước khi triển khai FE single-store

Ngày đối chiếu: 2026-10-04. Tài liệu này ghi lại readiness contract trong [FE handoff admin-first](./admin-first-api-and-screen-design.md), [phạm vi M1 single-store](../m1/m1-single-store-business-scope.md), [roadmap M1](../m1/module-renewal-roadmap.md) và các thay đổi CR-01–CR-09 đã triển khai. Các kiểm tra runtime và giới hạn xác minh được ghi ở cuối tài liệu.

Phạm vi: một cửa hàng, `USER` mua hàng, `ADMIN` vận hành; VNPay, fulfillment và refund thủ công theo M1. “Trước FE” dưới đây nghĩa là trước khi FE **triển khai màn hình phụ thuộc**. Không cần đợi mọi mục tùy chọn hoàn tất mới bắt đầu thiết kế hoặc làm FE không phụ thuộc chúng.

## 1. Kết luận và thứ tự ưu tiên

Backend đã có phần lớn đường mua hàng `catalog → cart → checkout → payment → order → cancellation/refund`. Contract dưới đây đã được chốt để FE triển khai các màn hình phụ thuộc:

| ID | Mức cần thiết | Luồng FE | Việc cần chốt ở BE | Đề xuất |
| --- | --- | --- | --- | --- |
| BE-01 | Bắt buộc trước A0 | Đăng nhập admin | Cấp `ADMIN` trên database mới | Tài liệu hóa quy trình bootstrap một lần, không tạo admin qua đăng ký công khai. |
| BE-02 | Hoàn tất cho A1 write | Sửa/xóa category | `Category.version` được trả trong admin read; update/delete nhận lại token, hierarchy writes làm tăng version của row bị đổi | FE gửi version từ GET và tải lại khi nhận `409` stale token. |
| BE-03 | Hoàn tất trước S1 | Lọc sản phẩm | Price, stock và option có thể khớp ở các SKU khác nhau của cùng Product | Khi có option/price, áp các điều kiện SKU trong cùng một truy vấn `EXISTS`; giữ price range summary toàn Product. |
| BE-04 | Hoàn tất trước S6/S7 | Checkout và retry payment | Lỗi payment initialization sau commit không được đánh đồng với checkout chưa tạo Order | Lỗi retryable trả Order cùng `payment: null`; lỗi khác vẫn là Problem Details có `orderId`. |
| BE-05 | Hoàn tất trước A7 | Danh sách đơn admin | Thứ tự ổn định và giới hạn trang | `createdAt DESC, id DESC`; `pageNum >= 1`, `pageSize` 1–20. |
| BE-06 | Hoàn tất cho refund inbox | Xử lý refund | Cần hàng đợi refund có phân trang | `GET /admin/refunds?status=&pageNum=&pageSize=`, thứ tự `requestedAt ASC, id ASC`. |
| BE-07 | Hoàn tất cho A7 search | Tìm đơn | Tìm chính xác tracking number | Kết hợp exact `trackingNumber` với hai status filter trong admin `GET /orders`. |
| BE-08 | Hoàn tất trước S1/S2 | Product slug | Slug viết mới cần nhất quán mà không làm mất URL legacy | Chuẩn hóa create/update/availability; giữ nguyên slug legacy đã lưu. |
| BE-09 | Hoàn tất cho A4 | Product list và tồn kho | Cần tổng giá/tồn trên Product list, không gọi từng SKU | `GET /admin/products` phân trang theo Product; min/max giá effective và tổng `onHand/reserved/available` trên Variant ACTIVE. |

**Mốc vào FE admin vòng đầu:** BE-01, BE-02, BE-05, BE-06, BE-07 và BE-09 đã có contract. **Mốc vào FE storefront mua hàng:** BE-03, BE-04 và BE-08 đã có contract.

## 2. Phương án và tiêu chí hoàn thành

### BE-01 — Tạo tài khoản ADMIN đầu tiên

**Hiện trạng.** `POST /auth/register` và tạo user qua OAuth đều gán `Role.USER` ([AuthServiceImpl](../../src/main/java/com/xdpsx/ecommerce/auth/application/AuthServiceImpl.java), [CustomOAuth2UserService](../../src/main/java/com/xdpsx/ecommerce/auth/infrastructure/security/oauth2/CustomOAuth2UserService.java)); [README](../../README.md) hướng dẫn bootstrap dev bằng runner opt-in và phương án SQL thủ công. Điều này giữ đúng nguyên tắc không mở public admin registration nhưng A0 cần một tài khoản có thể đăng nhập.

- **Phương án A — đề xuất cho M1:** trong `dev`, cho phép runner opt-in nâng tài khoản LOCAL đã tạo bằng luồng đăng ký theo email canonical được cấu hình; không tạo user hoặc lưu mật khẩu mặc định. Giữ phương án người có quyền DB đổi role làm thủ công/fallback. Đăng nhập lại để nhận token với role mới.
- **Phương án B:** công cụ bootstrap chạy một lần từ command line/config bảo mật. Chỉ chọn nếu thường xuyên dựng môi trường mới; cần quy tắc chống chạy lại và quản lý credential.
- **Hoàn thành khi:** môi trường sạch có thể tạo admin theo hướng dẫn lặp lại được; đăng ký công khai vẫn chỉ tạo `USER`; tài liệu nêu rõ ai được thực hiện bước cấp quyền và cách xác minh.

### BE-02 — Token xung đột cho Category (đã triển khai)

**Hiện trạng.** `GET /admin/categories/**` trả [AdminCategoryResponse](../../src/main/java/com/xdpsx/ecommerce/catalog/category/api/dto/AdminCategoryResponse.java) với `version`; [update request](../../src/main/java/com/xdpsx/ecommerce/catalog/category/api/dto/UpdateCategoryRequest.java) và delete nhận lại cùng token. [Service](../../src/main/java/com/xdpsx/ecommerce/catalog/category/application/CategoryServiceImpl.java) từ chối token cũ trước khi đổi metadata hoặc xóa; JPA `@Version` bảo vệ write thực tế. Move, reorder và renumber sibling cũng làm version của row bị thay đổi tăng lên.

- **Phương án A:** trả `updatedAt`, nhận lại giá trị đó và so sánh theo semantics được chốt. Ít đổi schema nhưng phải xử lý precision/timezone và các thao tác hierarchy cũng cần làm mới mốc.
- **Phương án B — đã triển khai:** thêm `@Version`/cột `version` như Brand; trả `version` ở admin list/detail và yêu cầu cùng giá trị khi update/delete. Liquibase changeset mới đã backfill row cũ về `0`; JPA cũng tăng token cho move/reorder/renumber.
- **Hoàn thành khi:** đọc category rồi update/delete với token hợp lệ thành công; token cũ bị từ chối bằng `409`; hai admin sửa cùng node không ghi đè âm thầm. Cập nhật A1 và OpenAPI trong cùng CR.

### BE-03 — Lọc Product theo cùng SKU

**Hiện trạng trước CR-03.** [ProductSpecification](../../src/main/java/com/xdpsx/ecommerce/catalog/product/persistence/ProductSpecification.java) kết hợp điều kiện `hasPriceInRange`, `isInStock`, `hasMatchingActiveVariant` độc lập. Mỗi điều kiện có thể được thỏa bởi một Variant khác nhau. Ví dụ Product có SKU xanh giá 200.000đ và SKU đỏ giá 100.000đ vẫn có thể xuất hiện khi lọc xanh + giá tối đa 150.000đ.

- **CR-03 đã triển khai:** khi có option hoặc min/max price, một predicate `EXISTS` trên Variant đủ điều kiện bán yêu cầu option/value, effective price tại cùng thời điểm và availability của **chính Variant đó**. `inStock=false` cũng yêu cầu SKU khớp option/giá đang hết hàng. Khi không có option/price, `inStock=false` vẫn nghĩa là toàn Product không có SKU đủ điều kiện còn hàng. Giá summary giữ range của tất cả Variant đủ điều kiện trong Product. Visibility và category/brand filter vẫn ở Product.
- **Phương án B:** giữ semantics “có một SKU cho mỗi điều kiện” và mô tả rõ trên UI. Không đề xuất vì khách thường hiểu bộ lọc kết hợp chỉ một mặt hàng có thể mua.
- **Hoàn thành:** Product không xuất hiện nếu price/stock/option chỉ khớp ở các SKU khác nhau; vẫn xuất hiện nếu một SKU đáp ứng tất cả; pagination/count không lặp Product. Persistence test bảo vệ cả trường hợp hết hàng trên cùng SKU.

### BE-04 — Checkout khi khởi tạo payment thất bại

**Đã triển khai.** Sau khi Order commit, lỗi `PAYMENT_INITIALIZATION_FAILED` trả checkout body có Order và `payment: null`; replay cùng idempotency key giữ Order và thử khởi tạo payment lại. Lỗi prepare/invariant vẫn là Problem Details có `orderId`; lỗi runtime ngoài dự kiến là `INTERNAL_ERROR` có `orderId`. Lỗi trước khi tạo Order vẫn giữ response lỗi. HTTP status lần đầu/replay vẫn là `201`/`200`. Đây là contract FE đã ghi ở handoff.

### BE-05 — Danh sách Order có phân trang ổn định

**Đã triển khai.** Admin `GET /orders` sort `createdAt DESC, id DESC`; `pageNum >= 1`, `pageSize` 1–20 ở cả admin/customer order list; sai trả 400. Customer list giữ sort updatedAt (fallback createdAt) hiện tại, thêm `id DESC` tie-breaker.

- **Phương án A — đề xuất:** sort mặc định `createdAt DESC, id DESC` cho admin list; ràng buộc trang và kích thước hợp lệ, trả `400` cho giá trị sai. Giữ filter status hiện tại.
- **Phương án B:** thêm sort tùy chọn có allowlist khi A7 cần thay đổi thứ tự; vẫn cần sort mặc định và tie-breaker `id`.
- **Hoàn thành khi:** các trang trả thứ tự ổn định khi nhiều đơn cùng thời điểm; input page sai trả lỗi client có kiểm soát; test query hoặc API đại diện.

### BE-06/07 — Tác vụ Order và Refund trong Backoffice

**Refund.** [AdminRefundController](../../src/main/java/com/xdpsx/ecommerce/refund/api/AdminRefundController.java) có `GET /admin/refunds?status=&pageNum=&pageSize=` với `orderId/trackingNumber`, page size 1–20 và sort `requestedAt ASC, id ASC`. Mutations complete/fail/retry tiếp tục là xử lý thủ công.

**Tìm đơn.** Admin `GET /orders` hỗ trợ exact `trackingNumber` kết hợp với `orderStatus`/`paymentStatus`. Chưa thêm date range hoặc customer search.

### BE-08 — Product slug trước URL storefront

**Đã triển khai.** Product create, explicit slug update và slug-availability dùng canonicalization như Category. Rename không tự tạo slug; form gửi lại nguyên slug legacy đang lưu sẽ giữ URL. Không backfill vì repo không có deployment dataset/URL consumer để lập redirect an toàn; slug cũ tiếp tục đọc được.

- **Phương án A — đề xuất:** dùng cùng quy tắc Category cho Product, normalize ở server, rename không tự đổi slug; đổi slug chỉ khi admin gửi có chủ đích. Audit/backfill slug cũ và xung đột trước khi siết validation/database constraint.
- **Phương án B:** giữ slug tự do và định nghĩa lại URL/encoding. Chỉ chọn nếu có use case thực tế cần ký tự ngoài quy tắc trên.
- **Hoàn thành khi:** URL Product nhất quán, unique ở DB, dữ liệu cũ đọc được hoặc được migrate có kế hoạch; test tạo/sửa và slug trùng/không hợp lệ.

### BE-09 — Dữ liệu tổng hợp cho Product/Inventory (đã triển khai)

Admin `GET /admin/products` bổ sung `minimumPrice`, `maximumPrice`, `onHand`, `reserved`, `available` cho từng Product trong trang. Query batch tính giá effective tại một thời điểm chung trên Variant ACTIVE và tổng inventory của Variant ACTIVE; `available` là tổng `onHand - reserved`. Không có Variant ACTIVE thì giá null và các số tồn bằng 0. Query aggregate chạy theo ID trong trang nên không tạo N+1.

## 3. Các khoảng trống trong handoff không chặn FE vòng đầu

| Khoảng trống | Quyết định đề xuất |
| --- | --- |
| Dashboard KPI, báo cáo, customer management, payment attempt list | Admin landing ban đầu là trang điều hướng; không dựng số liệu giả. Tách CR khi có câu hỏi vận hành và dữ liệu cần hiển thị. |
| Lịch sử inventory adjustment | Audit record đã được ghi trong BE; thêm read API khi A6 cần xem lịch sử/đối soát. |
| `/orders` và `/media` nằm ngoài `/admin` | FE dùng path hiện tại. Nếu đổi namespace, làm CR riêng với kế hoạch compatibility và cập nhật OpenAPI/handoff. |
| OAuth/social login | Chỉ đưa nút vào FE sau khi xác nhận session/refresh và account linking contract; login LOCAL đủ cho A0 vòng đầu. |
| Media cleanup observability/orphan reconciliation | Follow-up vận hành trong roadmap; ưu tiên khi storage thật hoặc lỗi cleanup cần điều tra. |
| Vận đơn, hãng vận chuyển, return/RTO sau `SHIPPED`, refund tự động, partial refund, multi-vendor | Ngoài phạm vi M1 đã chốt; không biến thành điều kiện vào FE single-store. |

## 4. Cách tách CR và bàn giao

Các CR đã triển khai gồm admin bootstrap, Category concurrency, Product filter cùng SKU, checkout/payment failure, Order list sorting/validation, Product slug, refund inbox, Order search và projection tồn kho. Khi contract đổi tiếp, kiểm tra `API → service → persistence → database → test`, dùng Liquibase nếu đổi schema, cập nhật [FE handoff](./admin-first-api-and-screen-design.md) và OpenAPI.

Trước khi FE gọi API thật, chạy backend và đối chiếu `/v3/api-docs`, request/response thực tế và status lỗi. Maven wrapper vẫn báo `Cannot index into a null array`, nhưng Maven 3.9.16 trong cache đã chạy trực tiếp: `clean test-compile`, targeted Order/Refund tests (15 tests, gồm MySQL Testcontainers) và `spotless:check` đều thành công. Category hierarchy MySQL persistence tests cũng chạy đạt trong lượt xác minh. Chưa chạy toàn bộ test suite hoặc runtime API walkthrough.
