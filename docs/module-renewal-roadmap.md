# Module Renewal Roadmap

Tài liệu này là checklist sống cho quá trình đi lại từng module trong milestone M1.
Mỗi lần audit một module, bổ sung các phát hiện đã xác minh vào đúng mục thay vì
biến chúng thành thay đổi ngoài phạm vi của CR đang làm.

## Cách sử dụng

- Mỗi module nên đi qua đủ luồng: API -> application/domain -> persistence -> database -> test.
- Tách một CR cho một thay đổi nghiệp vụ hoặc migration có thể review độc lập.
- Đánh dấu `[x]` chỉ khi hành vi, migration và test đại diện đều hoàn tất.
- Ghi rõ mục nào là lỗi đã quan sát, mục nào mới là câu hỏi nghiệp vụ cần quyết định.
- Không giữ compatibility với API/schema cũ nếu chưa xác nhận có consumer cần nó.

## Thứ tự đề xuất

1. Engineering baseline và security containment
2. Category
3. Brand
4. Product catalog và tích hợp Media
5. Product Variant/SKU
6. Inventory
7. Pricing và Promotion
8. User/Auth/Address
9. Cart
10. Checkout và Order
11. Payment
12. Fulfillment, Cancellation và Refund

Thứ tự này nhằm ổn định dependency từ dưới lên. Cart phải tham chiếu đúng đơn vị
bán (thường là SKU), Order phải snapshot được giá và sản phẩm, còn Payment chỉ nên
được hoàn thiện sau khi vòng đời Order đã rõ.

## 0. Media - đã hoàn tất vòng đầu

Trạng thái: `[x]` lifecycle/storage boundary cơ bản, `[ ]` các follow-up bên dưới.

### Đã có

- [x] `MediaPurpose` và `MediaStatus` thay cho các flag rời rạc.
- [x] Upload tạo media `TEMPORARY`; aggregate activate media khi attach.
- [x] Media API chỉ discard media `TEMPORARY`.
- [x] Cleanup xử lý media hết hạn hoặc `PENDING_DELETE` qua storage abstraction.
- [x] Brand và Category đã dùng query theo cả status và purpose khi attach.

### Follow-up đã quan sát

- [ ] Quyết định và bảo vệ concurrency khi hai transaction cùng attach một media
      `TEMPORARY`. Query hiện không lock/version và schema chưa đảm bảo một media
      chỉ thuộc một aggregate.
- [ ] Quyết định retry/observability cho cleanup thất bại và cách phát hiện asset
      mồ côi khi database/storage lệch nhau.
- [ ] Khi Product được sửa, loại bỏ toàn bộ đường upload/delete Cloudinary cũ để
      chỉ còn một Media flow.

## 1. Engineering baseline và security containment

Đây là gate trước các CR nghiệp vụ tiếp theo, không phải một đợt redesign.

### Vấn đề đã quan sát

- [ ] `mvnw.cmd` hiện lỗi trước khi Maven khởi động (`Cannot index into a null
    array`), nên local verification chưa đáng tin cậy.
- [ ] Quy ước/CI nói Java 17 nhưng `pom.xml` cấu hình Java 25; cần chọn một baseline
      duy nhất và đồng bộ local, CI, README.
- [ ] `SecurityConfig` kết thúc authorization bằng `.anyRequest().permitAll()`;
      các API ghi và admin hiện không được bảo vệ ở HTTP boundary.

### Exit criteria

- Wrapper chạy được `spotless:check` và `test` trên cùng JDK với CI.
- Có test context/security tối thiểu cho public, authenticated và admin endpoint.
- Không thay đổi framework/dependency nếu không có lỗi cụ thể cần giải quyết.

## 2. Category - đã hoàn tất vòng đầu

Trạng thái: `[x]` model/API boundaries, `[x]` hierarchy operations, `[x]`
storefront read model.

### Đã hoàn tất

- [x] Model/API boundaries: invariant cây (`MAX_DEPTH = 3`, không tự làm parent, không
  cycle), tách rõ admin detail với public detail, validation query parameter admin
  (`sort`, `level`) trả `400` thay vì `500`.
- [x] Hierarchy operations: create, update metadata/status/image, move, reorder, delete
  với bảo vệ `RESOURCE_IN_USE` khi category đang được tham chiếu.
- [x] Storefront read model: root list, full visible tree và detail theo slug.

### Follow-up đã quan sát

- [ ] Chuẩn hóa image update giữa Category và Brand. Hiện Category copy
      `updateCategoryImage()`, còn Brand dùng `AbstractImageUpdatableService`.
- [ ] Đánh giá/thay thế timestamp concurrency bằng `@Version` khi có nhu cầu cụ thể.

## 3. Brand

Trạng thái: `[x]` model/admin API boundaries (CR1), `[x]` write invariants, concurrency
và delete safety (CR2), `[ ]` storefront read model (CR3 đã implement và verify trong
PR #41, đang chờ merge).

### Điểm cần audit

- [x] Chốt quan hệ Brand-Category: association là optional; category được gắn phải
      effectively active; create/update dùng semantics null/empty/replace đã thống nhất.
- [x] Tách public/admin query và thay `publicFlg` bằng `BrandStatus.ACTIVE/INACTIVE`.
- [x] Chốt tên Brand được trim và unique không phân biệt hoa/thường.
- [x] Kiểm tra delete brand đang được Product tham chiếu; không dựa riêng vào lỗi FK
      để biểu diễn nghiệp vụ.
- [x] Giữ attachment Media theo `BRAND_LOGO` với lifecycle đúng khi attach/replace/remove.
      Concurrency ownership giữa nhiều aggregate vẫn là Media follow-up riêng.

### Test nhỏ nhất có ý nghĩa

- [x] Create/update với category hợp lệ và logo đúng purpose.
- [x] Từ chối category/media không hợp lệ.
- [x] Từ chối xóa brand đang được Product sử dụng.

## 4. Product catalog và tích hợp Media

Đây là CR chuyển Product khỏi Cloudinary flow cũ; nên hoàn tất trước khi thiết kế
Cart mới.

### Vấn đề đã quan sát

- [ ] `ProductCreateRequest`/`ProductUpdateRequest` vẫn nhận
      `List<MultipartFile>` thay vì `imageIds`.
- [ ] `ProductImage` vẫn lưu URL, chưa tham chiếu `Media`, chưa có `displayOrder`.
- [ ] `ProductServiceImpl` vẫn upload/delete bằng `CloudinaryUploader` trực tiếp.
- [ ] `ProductMapper` vẫn dùng `CloudinaryUploader` để dựng URL.
- [ ] Schema còn `products.main_image` và `product_images.url`; chưa có migration
      sang `media_id` và thứ tự hiển thị.
- [ ] Product hiện không có test.

### Quyết định cần chốt trước implementation

- Main image là image có `displayOrder = 0`, một cờ riêng, hay field trên Product.
- Update images là replace toàn bộ ordered list hay add/remove/reorder riêng.
- Giới hạn số ảnh, uniqueness của media trong một product và hành vi khi request
  chứa trùng ID.
- Product có thể tồn tại khi Category/Brand private hay đã bị ngừng sử dụng không.
- Migration dữ liệu URL cũ: backfill được external ID hay phải coi là legacy URL.

### Test nhỏ nhất có ý nghĩa

- Create product attach ordered temporary media đúng purpose.
- Từ chối active/wrong-purpose/duplicate media và rollback toàn bộ transaction.
- Update remove/reorder images, đánh dấu media bỏ đi là `PENDING_DELETE`.
- Delete product chuyển toàn bộ media liên quan sang cleanup flow.
- Migration test cho dữ liệu product image hiện có.

## 5. Product Variant/SKU

Không nên hoàn thiện Cart trên `product_id` nếu sản phẩm thực tế sẽ bán theo SKU.

### Câu hỏi nghiệp vụ

- [ ] Thuộc tính nào tạo variant (size, color, v.v.) và tổ hợp nào hợp lệ.
- [ ] SKU uniqueness, trạng thái bán, barcode và ảnh theo product hay variant.
- [ ] Giá nằm ở Product hay SKU; Product hiển thị một giá hay khoảng giá.
- [ ] Có cho thay đổi/xóa variant đã xuất hiện trong Order hay chỉ deactivate.

### Exit criteria

- SKU là đơn vị bán ổn định mà Cart, Inventory và Order có thể tham chiếu.
- Database có unique constraint và index phù hợp, không chỉ validation ở service.
- Có test tạo tổ hợp, trùng SKU và deactivate SKU đã được tham chiếu.

## 6. Inventory

Nên tách khỏi boolean `Product.inStock` trước Cart/Checkout.

### Câu hỏi nghiệp vụ

- [ ] Mô hình `onHand`, `reserved`, `available`; inventory thuộc SKU nào.
- [ ] Reserve ở lúc checkout hay payment; timeout/release reservation.
- [ ] Chính sách oversell và cách chống lost update dưới concurrency.
- [ ] Điều chỉnh tồn kho có cần reason/audit trail hay chưa.

### Test nhỏ nhất có ý nghĩa

- Nhập/điều chỉnh tồn và tính available.
- Hai checkout cạnh tranh không bán vượt tồn.
- Release reservation khi order/payment thất bại hoặc hết hạn.

## 7. Pricing và Promotion

### Vấn đề/câu hỏi cần audit

- [ ] Loại bỏ model `discountPercent` đơn giản nếu không đủ nghiệp vụ đã chọn.
- [ ] Chốt money type, currency, rounding và range constraint đồng nhất.
- [ ] Giá hiệu lực theo thời gian, giá SKU và rule promotion có thật sự cần trong M1.
- [ ] Xác định thời điểm chốt giá; OrderItem phải snapshot unit price/discount thay vì
      đọc lại Product sau này.

Không cần xây promotion engine tổng quát nếu M1 chỉ cần giá thường và một loại giảm
giá cụ thể.

## 8. User/Auth/Address

### Vấn đề đã quan sát

- [ ] `users.email` chưa có unique constraint trong changeset khởi tạo dù register
      kiểm tra `existsByEmail` ở application layer.
- [ ] Chốt canonicalization của email và behavior khi tài khoản LOCAL/OAuth trùng email.
- [ ] Chốt access token lifecycle; refresh/revocation chỉ thêm nếu use case yêu cầu.
- [ ] Address nên là dữ liệu riêng hay snapshot request; Order luôn phải giữ snapshot
      giao hàng không phụ thuộc profile về sau.
- [ ] Rà ownership và role authorization trên toàn bộ customer/admin API.

### Test nhỏ nhất có ý nghĩa

- Register/login thành công và email trùng.
- OAuth/local account-linking theo policy đã chọn.
- Customer không truy cập tài nguyên của customer khác; admin boundary hoạt động.

## 9. Cart

### Vấn đề đã quan sát

- [ ] Cart hiện dùng khóa `(user_id, product_id)` và chưa hỗ trợ SKU.
- [ ] Add/update không có transaction boundary và thao tác read-modify-write có thể
      lost update khi request đồng thời.
- [ ] Chưa thấy rule quantity dương/tối đa, product published/saleable, SKU active
      hoặc stock availability.
- [ ] Chốt cart có reserve tồn không; mặc định nên chỉ kiểm tra sơ bộ và reserve ở
      checkout để tránh giữ stock quá sớm.
- [ ] DTO hiển thị giá hiện tại hay price estimate cần ghi rõ; Cart không phải nguồn
      giá cuối cùng của Order.

### Test nhỏ nhất có ý nghĩa

- Add cùng SKU tăng quantity theo policy.
- Từ chối quantity/SKU không hợp lệ.
- User không sửa/xóa cart item của user khác.
- Case concurrent update nếu dùng atomic update/versioning.

## 10. Checkout và Order

Order nên được tách khỏi việc khởi tạo payment provider để transaction DB không bao
quanh network call.

### Vấn đề đã quan sát

- [ ] `placeOrder()` chỉ lấy cart item có `inStock`, tính giá từ Product hiện tại và
      chưa reserve/decrement inventory.
- [ ] `OrderItem` chỉ giữ Product và quantity; chưa snapshot SKU/name/unit price,
      discount hay subtotal.
- [ ] Việc xóa cart sau order đang bị comment out.
- [ ] `placeOrder()` gọi `paymentService.init()` trong transaction DB.
- [ ] State transition của Order đang được set trực tiếp; cần ma trận trạng thái hợp lệ.
- [ ] Chốt idempotency cho submit checkout để retry không tạo hai order.

### Test nhỏ nhất có ý nghĩa

- Checkout snapshot đúng giá/sản phẩm/address và xử lý cart theo policy.
- Từ chối thiếu tồn với rollback nhất quán.
- Từ chối transition order không hợp lệ.
- Retry cùng idempotency key không tạo duplicate order.

## 11. Payment

### Vấn đề đã quan sát

- [ ] `PaymentService` hiện chỉ có `init`; lifecycle callback/IPN nằm chủ yếu ở
      infrastructure và cần nối rõ với state machine của Payment/Order.
- [ ] `OrderServiceImpl.payment()` có thể set `PAID` trực tiếp; cần chỉ tin callback
      đã xác thực và đối chiếu amount/order/transaction reference.
- [ ] Chốt idempotency khi provider gửi IPN lặp, out-of-order hoặc retry.
- [ ] Tách payment attempt/transaction nếu một order có thể thanh toán lại.
- [ ] Không log URL/query chứa dữ liệu nhạy cảm nếu provider đưa signature/token vào đó.

### Test nhỏ nhất có ý nghĩa

- Init payment đúng amount và reference.
- IPN signature sai bị từ chối.
- Callback hợp lệ cập nhật đúng một lần; callback lặp không gây side effect lặp.
- Amount/reference không khớp không được đánh dấu paid.

## 12. Fulfillment, Cancellation và Refund

Đây là phần sau cùng vì phụ thuộc Order, Inventory và Payment lifecycle.

### Câu hỏi nghiệp vụ

- [ ] Transition `PENDING -> PROCESSING -> SHIPPED -> DELIVERED` và actor được phép.
- [ ] Customer/admin được cancel ở trạng thái nào; tồn kho được release/restock khi nào.
- [ ] Refund toàn phần/một phần có nằm trong M1 không.
- [ ] Order đã thanh toán nhưng fulfillment thất bại được xử lý thế nào.

Chỉ triển khai những flow có acceptance criteria cụ thể; chưa cần tích hợp hãng vận
chuyển hoặc hệ thống hoàn tiền thật trong M1.

## Nguyên tắc cắt CR

Một module không đồng nghĩa với một CR lớn. Ưu tiên chuỗi nhỏ như:

1. Chốt invariant và API contract.
2. Sửa domain/application behavior cùng test đại diện.
3. Thêm changeset Liquibase nếu schema thay đổi.
4. Review diff hoàn chỉnh với phase branch.

Nếu một quyết định làm thay đổi identity của entity được downstream tham chiếu, ví
dụ Cart chuyển từ Product sang SKU, phải hoàn tất quyết định đó trước khi refactor
downstream để tránh làm lại hai lần.
