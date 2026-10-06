# Kế hoạch CR: BE readiness cho FE single-store

Trạng thái: **CR-01…CR-09 đã triển khai**. Đối chiếu code ngày 2026-10-04 trên `phase/m1-ecommerce-renewal`.

Nguồn phạm vi: [BE readiness](./backend-readiness-before-frontend.md), [FE handoff](./admin-first-api-and-screen-design.md), [M1 single-store scope](../m1/m1-single-store-business-scope.md). `CR-01`…`CR-09` là mã trong **file plan này**, chưa phải số issue/branch. Mỗi CR là một thay đổi có thể review riêng; tạo issue/branch/PR theo [change workflow](../change-workflow.md) khi thực hiện, không tạo chúng trong bước lập plan.

## Thứ tự và mốc bàn giao

| CR | Readiness | Ưu tiên thực hiện | FE phụ thuộc | Phụ thuộc BE |
| --- | --- | --- | --- | --- |
| CR-01 Admin bootstrap | BE-01 | Admin gate | A0 | Không |
| CR-02 Category concurrency | BE-02 | Admin gate | A1 write | Không; cần migration |
| CR-03 Storefront filter cùng SKU | BE-03 | Storefront gate | S1 | Không |
| CR-04 Checkout/payment failure contract | BE-04 | Storefront gate | S6/S7 | Không; contract đã chốt |
| CR-05 Order list pagination | BE-05 | Admin gate | A7 | Không |
| CR-06 Product slug | BE-08 | Storefront gate | S1/S2 | Không; legacy slug được giữ nguyên |
| CR-07 Refund inbox | BE-06 | Admin gate | Refund inbox/A8 | Đã kích hoạt theo yêu cầu |
| CR-08 Admin order lookup | BE-07 | Admin gate | A7 có tìm mã đơn | Đã kích hoạt theo yêu cầu |
| CR-09 Product/inventory projection | BE-09 | Admin gate | A4 hiển thị giá/tồn trên list | Đã kích hoạt theo yêu cầu |

Thứ tự gợi ý cho FE vẫn là **admin-first**: CR-01, CR-05, CR-02, CR-07, CR-08, CR-09 cho backoffice; CR-03, CR-06, CR-04 cho storefront. CR-01–CR-09 đã được triển khai; mỗi contract đổi đều cập nhật FE handoff/OpenAPI và có test bảo vệ hành vi tương ứng.

## CR-01 — Hướng dẫn bootstrap ADMIN đầu tiên (BE-01)

**Phạm vi đề xuất.** Giữ đăng ký public luôn tạo `USER`. Hỗ trợ bootstrap dev bằng `ApplicationRunner` chỉ chạy với profile `dev` và property opt-in; runner chỉ nâng đúng tài khoản `LOCAL` đã đăng ký theo email cấu hình, không tạo user hoặc seed mật khẩu. Giữ thao tác DB có quyền làm phương án thủ công. Không mở endpoint tự nâng role.

- **Hiện trạng:** [AuthServiceImpl](../../src/main/java/com/xdpsx/ecommerce/auth/application/AuthServiceImpl.java) tạo `Role.USER`; [TokenProvider](../../src/main/java/com/xdpsx/ecommerce/auth/infrastructure/security/TokenProvider.java) đóng role vào JWT; [README](../../README.md) chưa có bước bootstrap.
- **Bước làm:** thêm runner trong `dev` có opt-in rõ ràng và email canonical; chỉ nâng `LOCAL` user hiện có, không thay đổi tài khoản provider khác, không tạo account khi chưa tồn tại. Ghi cách đăng ký trước, bật runner, restart, tắt opt-in sau bootstrap, đăng nhập lại để lấy token mới, gọi `GET /users/me` và một route `/admin/**`; giữ SQL thủ công có điều kiện và hướng dẫn xử lý email sai/tài khoản đã tồn tại.
- **File dự kiến:** dev bootstrap runner, cấu hình `application-dev.yml`/`.env.example`, README; không đổi schema.
- **Nghiệm thu:** một database dev sạch tạo được admin bằng public registration và runner opt-in mà không sửa code; public registration vẫn ra `USER`; runner không chạy ngoài profile `dev`, không đổi role nếu chưa bật opt-in hoặc tài khoản không phải LOCAL; token mới vào được admin route, token USER không vào được.
- **Xác minh:** walkthrough trên môi trường dev; test đăng ký USER hiện có trong `AuthServiceImplTest`. Không thêm unit test chỉ để kiểm tra câu chữ tài liệu.
- **Rủi ro/quyết định:** cập nhật role trong DB chỉ dành cho người vận hành có quyền; không dùng token được cấp trước khi đổi role để kiểm tra authorization.

## CR-02 — Category concurrency token cho A1 (BE-02)

**Phạm vi.** Thay `lastRetrievedAt` trên update/delete bằng `version` do server cấp, theo mẫu Brand. Giữ hierarchy lock hiện có; không thiết kế lại move/reorder trong cùng CR.

- **Hiện trạng:** `AdminCategoryResponse` thiếu token; `UpdateCategoryRequest` và delete nhận timestamp; `CategoryServiceImpl` kiểm tra timestamp client gửi. [Brand](../../src/main/java/com/xdpsx/ecommerce/catalog/brand/domain/Brand.java) đã dùng `@Version`.
- **Đã làm:** thêm `Category.version`; changeset mới backfill `0` và đặt `NOT NULL`; admin list/detail trả token; update/delete so sánh token trước mutation và JPA bảo vệ write thực tế; move/reorder và renumber sibling tăng version khi row thay đổi; cập nhật API docs và FE handoff.
- **File:** `Category`, admin DTOs, `CategoryMapper`, `CategoryServiceImpl`, `CategoryService`, controllers/API docs, changeset 25, `db.changelog-master.yaml`, Category service/persistence/API/OpenAPI tests. `ModifyExclusiveDTO` chỉ được Category dùng nên đã thay bằng `DeleteCategoryRequest` riêng.
- **Nghiệm thu:** GET trả `version`; update/delete với version hiện hành thành công; token cũ trả `409`; hai write cạnh tranh không ghi đè; move/reorder không âm thầm làm mất metadata.
- **Test tối thiểu:** service test thành công + stale token; hierarchy persistence test MySQL kiểm tra version qua Liquibase master changelog; migration test MySQL xác nhận backfill, `NOT NULL` và default `0`; cập nhật security/OpenAPI tests theo body mới.
- **Migration/compatibility:** đổi request contract của A1; sửa FE handoff cùng CR. Không sửa changeset đã áp dụng. Handoff chỉ là thiết kế, không thấy consumer FE trong repository, nên thay `lastRetrievedAt` bằng `version` trong cùng CR.

## CR-03 — Storefront filter theo cùng SKU (BE-03)

**Phạm vi.** Kết hợp option, price và stock trên cùng Variant đủ điều kiện bán khi có option/price filter. Giữ filter category/brand, visibility Product và price range toàn Product hiện tại.

- **Hiện trạng:** [ProductSpecification](../../src/main/java/com/xdpsx/ecommerce/catalog/product/persistence/ProductSpecification.java) ghép ba `EXISTS`/predicate độc lập; [ProductServiceImpl](../../src/main/java/com/xdpsx/ecommerce/catalog/product/application/ProductServiceImpl.java) trả product page và price range. Test hiện có bảo vệ option trong cùng SKU và hai biên giá trong cùng SKU, chưa bảo vệ **tổ hợp** option + price + stock.
- **Đã làm:** một Variant predicate áp option, effective price tại cùng thời điểm và availability của chính SKU đó. Khi không có option/price filter, `inStock=false` giữ semantics hiện tại là Product không còn SKU đủ điều kiện nào có hàng. Summary price range tiếp tục tính trên toàn Product.
- **File:** `ProductSpecification`, `ProductVisibilityPersistenceTest`, readiness và FE handoff. Không cần đổi response hoặc migration.
- **Nghiệm thu:** option, giá và stock phải khớp cùng SKU; `inStock=false` theo SKU đó khi có option/price; count/page không lặp Product; PLP vẫn trả range toàn Product.
- **Test:** persistence test kiểm tra trường hợp option/giá/stock khớp ở các SKU khác nhau bị loại, SKU đúng tổ hợp được trả, nhánh cùng SKU hết hàng, cùng pagination/count. Không cần migration.
- **Quyết định:** được chốt trước implementation: khi có option hoặc giá, `inStock=true/false` lọc chính SKU thỏa option/giá; khi không có option/giá, giữ semantics stock Product hiện tại. Price range hiển thị luôn là range toàn Product.

## CR-04 — Contract checkout sau lỗi khởi tạo payment (BE-04)

**Phạm vi.** Sau khi Order đã commit, lỗi payment initialization được phân loại là retryable trả `CheckoutResponse` với `order`, `payment: null`, `replayed`; lỗi trước commit vẫn trả Problem Details. Giữ retry endpoint và idempotency hiện tại.

- **Hiện trạng:** [CheckoutServiceImpl](../../src/main/java/com/xdpsx/ecommerce/checkout/application/CheckoutServiceImpl.java) gọi init sau `CheckoutTransactionService.execute()`; [PaymentAttemptService](../../src/main/java/com/xdpsx/ecommerce/payment/application/PaymentAttemptService.java) chuyển lỗi tạo URL thành `502` có `parameters.orderId`; lỗi từ bước prepare có thể có code khác. [CheckoutServiceImplTest](../../src/test/java/com/xdpsx/ecommerce/checkout/application/CheckoutServiceImplTest.java) đang mong exception.
- **Đã làm:** chỉ `PAYMENT_INITIALIZATION_FAILED` sau commit được trả thành checkout response với payment null. Lỗi chuẩn bị attempt/invariant vẫn là Problem Details và có `orderId`; runtime ngoài dự kiến được trả `INTERNAL_ERROR` với `orderId`. Controller vẫn trả `201` lần đầu, `200` replay.
- **File:** `CheckoutServiceImpl`, `CheckoutServiceImplTest`, FE handoff. Không cần migration.
- **Nghiệm thu:** khách biết Order đã tạo khi thiếu payment URL; retry cùng key giữ nguyên Order; lỗi trước commit vẫn trả lỗi. Failure test và replay test đã thêm.
- **Quyết định:** chọn phương án B theo handoff hiện tại; chỉ mã lỗi payment initialization đã phân loại là retryable mới tạo response checkout thành công.

## CR-05 — Phân trang Order ổn định và input hợp lệ (BE-05)

**Phạm vi.** Sort mặc định `createdAt DESC, id DESC` cho admin list; validate `pageNum/pageSize` với page size 1–20; customer list giữ sort timestamp hiện tại và thêm `id DESC` tie-breaker.

- **Hiện trạng:** [OrderServiceImpl](../../src/main/java/com/xdpsx/ecommerce/order/application/OrderServiceImpl.java) dùng `PageRequest.of(...)` không sort ở `getAllOrders`; [OrderController](../../src/main/java/com/xdpsx/ecommerce/order/api/OrderController.java) nhận `int` không ràng buộc. Customer list đã sort theo thời gian trong repository nhưng chưa có tie-breaker ID.
- **Đã làm:** admin sort `createdAt DESC, id DESC`; customer list tie-break `id DESC`; cả hai list validate page >=1 và page size 1–20. Giới hạn 20 theo `PageConstants.MAX_ITEMS_PER_PAGE`.
- **File:** `OrderServiceImpl`, `OrderRepository`, `OrderController`, service/security tests, `OrderRefundQueuePersistenceTest`.
- **Nghiệm thu:** sort có tie-break; input ngoài giới hạn trả 400 trước khi gọi service.
- **Test tối thiểu:** MySQL persistence test kiểm tra admin sort và customer timestamp/ID tie-break qua ranh giới trang; controller test cho tham số sai. Không cần migration trừ khi đo được query cần index.
- **Giới hạn:** offset pagination vẫn có thể dịch khi Order mới xuất hiện giữa hai request; FE cần refetch khi cần dữ liệu mới. Không đưa cursor pagination vào CR này.

## CR-06 — Chuẩn hóa Product slug (BE-08)

**Phạm vi.** Product slug mới được server chuẩn hóa theo quy tắc Category; rename không tự đổi URL; legacy slug giữ nguyên khi form gửi lại đúng giá trị hiện tại. Giữ DB uniqueness và không backfill URL đã lưu.

- **Hiện trạng:** `ProductCreateRequest`/`ProductUpdateRequest` chỉ kiểm tra không rỗng và độ dài; [ProductServiceImpl](../../src/main/java/com/xdpsx/ecommerce/catalog/product/application/ProductServiceImpl.java) dùng chuỗi client gửi để check/save và check availability; [Product](../../src/main/java/com/xdpsx/ecommerce/catalog/product/domain/Product.java) có unique slug ở DB.
- **Đã làm:** create, explicit update và slug-availability dùng `CategorySlug.normalize`; slug empty sau normalize trả lỗi 400 mới; Product rename giữ URL. Audit repository không có FE consumer/deployment dataset để backfill an toàn, vì vậy slug cũ được giữ nguyên và vẫn đọc được.
- **Nghiệm thu:** canonicalization đồng nhất, slug collision vẫn kiểm tra DB/service, legacy URL không bị đổi do edit field khác; service tests đã thêm.
- **Quyết định:** không migrate legacy slug trong CR này; URL cũ tiếp tục dùng được.

## CR đã kích hoạt theo yêu cầu triển khai

### CR-07 — Refund inbox (BE-06, đã triển khai)

Đã thêm `GET /admin/refunds?status=&pageNum=&pageSize=` với projection có `trackingNumber`, sort `requestedAt ASC, id ASC` và ADMIN-only access. Filter status là tùy chọn; page size 1–20. MySQL persistence test kiểm tra projection, filter status, count và tie-breaker timestamp qua nhiều trang. Dùng schema/index hiện tại, không đổi mutation/refund policy thủ công.

### CR-08 — Tra cứu Order admin (BE-07, đã triển khai)

Đã thêm exact `trackingNumber` vào admin `GET /orders`, kết hợp status filter hiện có. MySQL persistence test kiểm tra tracking kết hợp order/payment status. Route vẫn ADMIN-only; dùng uniqueness hiện tại, không thêm index hoặc tìm kiếm gần đúng.

### CR-09 — Projection Product/Inventory (BE-09, đã triển khai)

Admin `GET /admin/products` trả thêm `minimumPrice`, `maximumPrice`, `onHand`, `reserved`, `available`. Đơn vị phân trang là Product theo page contract hiện tại. Giá là min/max effective price ở cùng một `Instant now` trên các Variant ACTIVE; tồn kho là tổng của các Variant ACTIVE, `available = SUM(onHand - reserved)`, thiếu balance được hiểu là 0. Không lọc Product theo tồn kho. Giá null và tồn 0 nếu không có Variant ACTIVE. Aggregates được batch theo IDs của trang, tránh N+1; OpenAPI và persistence test đã cập nhật.

## Gate xác minh chung và vấn đề còn mở

- Mỗi CR chỉ chạy test liên quan trước, rồi `spotless:check` và suite phù hợp; migration cần test MySQL/Testcontainers. Cập nhật FE handoff trong cùng CR nếu route, DTO, status hoặc lỗi thay đổi; sau khi BE chạy, đối chiếu `/v3/api-docs` và request/response thực tế.
- Maven wrapper tiếp tục báo `Cannot index into a null array`, nên đã dùng Maven 3.9.16 trong cache trực tiếp. `clean test-compile`, targeted Order/Refund tests (15 tests, gồm 4 MySQL persistence cases) và `spotless:check` đều thành công; Category hierarchy MySQL persistence suite có 14 tests cũng thành công. Chưa chạy toàn bộ test suite hoặc runtime API walkthrough.
- Quyết định đã chốt: CR-04 trả checkout thành công với `payment: null` sau lỗi khởi tạo payment retryable; CR-06 chỉ chuẩn hóa slug mới hoặc slug được sửa rõ ràng, giữ nguyên URL legacy khi client gửi lại đúng giá trị hiện có. CR-07, CR-08 và CR-09 đã được kích hoạt theo yêu cầu và triển khai theo semantics ghi tại từng mục.
- Không gom dashboard, customer management, shipment carrier, refund tự động, partial refund, multi-vendor hoặc đổi toàn bộ prefix admin vào các CR trên. Chúng cần yêu cầu nghiệp vụ và plan riêng.
