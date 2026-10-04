# M1 Single-Store Business Scope

## 1. Trạng thái quyết định

Tài liệu này chốt phạm vi nghiệp vụ cho phần còn lại của milestone
**M1 - Ecommerce Domain Renewal**:

- Hệ thống phục vụ **một cửa hàng**.
- Cửa hàng có một nhóm vận hành duy nhất, hiện được biểu diễn bằng role `ADMIN`.
- Người dùng mua hàng được biểu diễn bằng role `USER`.
- Chưa triển khai marketplace, multi-vendor, seller onboarding hoặc phân quyền theo cửa hàng trong M1.
- Các module còn lại được hoàn thiện thành một luồng ecommerce single-store nhất quán trước khi mở rộng mô hình kinh doanh.

Quyết định này là chủ đích giới hạn phạm vi, không phải khẳng định rằng mô hình hiện tại đã phù hợp cho
multi-vendor. Khi có nhu cầu multi-vendor thật, hệ thống sẽ có một milestone riêng để bổ sung Store/Seller,
ownership và authorization tương ứng.

Tài liệu này bổ sung định hướng nghiệp vụ cho `module-renewal-roadmap.md`. Roadmap tiếp tục là checklist kỹ
thuật theo module; tài liệu này giải thích sản phẩm đang được xây cho ai, những quy tắc nào được giữ trong M1
và phần nào chủ động để lại cho giai đoạn sau.

## 2. Vì sao chọn single-store trước

Code và web hiện tại đều đang đi theo mô hình một cửa hàng với một bên vận hành. Tiếp tục theo hướng đó giúp:

- Hoàn thiện được một vòng đời mua hàng thực tế từ catalog đến fulfillment thay vì mở rộng actor khi các luồng
  cốt lõi còn thiếu.
- Tập trung vào những bài toán ecommerce quan trọng như SKU, tồn kho, giá, checkout, order, payment và hoàn
  tiền.
- Tránh thêm `Store`, `Seller`, tenant scope và permission model chỉ để chuẩn bị cho một yêu cầu chưa được triển
  khai.
- Giữ việc refactor theo từng module, đúng mục tiêu học nghiệp vụ trước rồi mới đưa thêm độ phức tạp hệ thống.

Multi-vendor không chỉ là thêm role `SELLER`. Nó còn yêu cầu ownership của Product/Media/Order, seller
membership, isolation giữa các cửa hàng, commission/payout, moderation và nhiều thay đổi downstream. Vì vậy
nó nên là một phase riêng thay vì chen vào giữa M1.

## 3. Actor và giao diện hệ thống trong M1

### 3.1 Actor

#### Customer (`USER`)

- Xem catalog công khai mà không cần đăng nhập.
- Đăng ký, đăng nhập và quản lý thông tin cá nhân cần thiết.
- Quản lý cart của chính mình.
- Checkout, thanh toán và xem order của chính mình.
- Yêu cầu cancellation/refund trong phạm vi policy được hỗ trợ.

#### Store operator (`ADMIN`)

`ADMIN` hiện vừa là chủ cửa hàng vừa là người vận hành. Admin có thể:

- Quản lý Media, Category, Brand, Product, Variant/SKU và các option/value dùng chung.
- Quản lý tồn kho, giá và promotion.
- Xem và xử lý order, payment, fulfillment, cancellation và refund.
- Thực hiện các thao tác vận hành toàn hệ thống single-store.

M1 không thêm role `SELLER`. Trong single-store, `SELLER` và `ADMIN` sẽ có cùng phạm vi dữ liệu nên việc tách
role chỉ làm tăng ceremony mà chưa tạo ra ranh giới bảo mật có ý nghĩa.

### 3.2 Giao diện

Hệ thống có hai nhóm giao diện:

1. **Storefront** dành cho khách mua hàng.
2. **Backoffice** dành cho người vận hành cửa hàng.

`Backoffice` là tên phù hợp hơn `Admin website` vì nó mô tả nơi thực hiện công việc vận hành. Trong M1, mọi
người dùng Backoffice đều là `ADMIN`; chưa cần tách một website riêng cho seller và một website riêng cho
platform admin.

Backend tiếp tục giữ boundary rõ ràng:

- Public/storefront read API dùng các resource path như `/products`, `/categories`, `/brands`.
- Backoffice write/read API dùng namespace `/admin/**` và yêu cầu role `ADMIN`.
- Customer API phải xác thực ownership theo user, không chỉ kiểm tra đã đăng nhập.

## 4. Media

### 4.1 Phạm vi đã phù hợp

Media là tài nguyên kỹ thuật dùng chung cho Category, Brand và Product, không phải một thư viện nội dung độc lập.
Lifecycle hiện tại được giữ:

```text
upload -> TEMPORARY -> attach -> ACTIVE
                         |
                         +-> remove/replace -> PENDING_DELETE -> physical cleanup
```

Các nguyên tắc cần giữ:

- Media có purpose rõ ràng như category image, brand logo hoặc product image.
- Chỉ Media `TEMPORARY` đúng purpose mới được attach.
- Một Media không được attach đồng thời vào nhiều aggregate qua application flow.
- Thay thế hoặc xóa ảnh chỉ đánh dấu `PENDING_DELETE`; cleanup chịu trách nhiệm xóa asset khỏi storage.
- Domain/application phụ thuộc `MediaStorage`, không phụ thuộc trực tiếp Cloudinary.

### 4.2 Upload qua backend

Upload Cloudinary qua backend là chấp nhận được trong M1 vì backend đang chịu trách nhiệm authorization,
validation, provider configuration, lifecycle và cleanup. Không cần direct upload chỉ để giống một hệ thống
lớn hơn.

Signed direct upload chỉ nên được xem xét khi xuất hiện nhu cầu cụ thể về bandwidth, latency hoặc tải backend.
Khi đó backend vẫn phải cấp upload authorization và nhận kết quả để tạo Media identity; frontend không được tự
quản lý asset ngoài lifecycle của hệ thống.

### 4.3 Follow-up trong single-store

- Chốt giới hạn file cho mọi environment, không chỉ development.
- Giữ validation loại file và kích thước phù hợp với từng purpose.
- Bổ sung retry/observability cho cleanup thất bại khi nhu cầu vận hành xuất hiện.
- Có cách phát hiện hoặc đối soát asset mồ côi khi database và storage lệch nhau.
- Không thêm `storeId`, `ownerId` hoặc media quota theo seller trong M1.

## 5. Catalog

### 5.1 Category

Category là taxonomy chung của cửa hàng và chỉ Admin quản lý.

Quy tắc hiện tại được giữ:

- Có hierarchy; một Category có tối đa một parent.
- Cây tối đa ba cấp trong M1.
- Không self-parent, không cycle và không vượt quá độ sâu cho phép.
- Category có thứ tự trong sibling group và hỗ trợ move/reorder.
- Status của node không cascade xuống descendant trong database.
- Category chỉ visible trên storefront khi bản thân và toàn bộ ancestor đều active.
- Không xóa Category đang có child hoặc đang được Brand/Product tham chiếu.
- Slug được normalize, ổn định và unique toàn hệ thống.

Một Product tiếp tục thuộc đúng một Category trong M1. Chỉ chuyển sang nhiều Category khi storefront hoặc
merchandising có use case cụ thể không thể giải quyết bằng taxonomy hiện tại.

### 5.2 Brand

Brand là dictionary chung do Admin quản lý:

- Brand có trạng thái active/inactive.
- Tên được chuẩn hóa theo contract hiện tại và unique không phân biệt hoa/thường.
- Category association là optional và phục vụ phân loại/filter.
- Brand chỉ visible trên storefront khi active.
- Không xóa Brand đang được Product tham chiếu.

M1 không có seller-submitted brand hoặc quy trình duyệt Brand.

### 5.3 Product

Product là phần thông tin chung mà mọi SKU chia sẻ:

- Tên, mô tả, slug, Category, Brand và bộ ảnh có thứ tự.
- Product mới luôn là draft.
- Update metadata không tự thay đổi publication state.
- Publish/unpublish là operation riêng.
- Product chỉ visible khi published, Brand active, Category effectively active và có ít nhất một Variant active.
- Product đã xuất hiện trong lịch sử Order không được hard delete làm mất tham chiếu lịch sử.

Giá và `inStock` hiện còn nằm trên Product chỉ là trạng thái chuyển tiếp. Chúng phải được thay thế bằng Pricing
và Inventory theo SKU trước khi Cart/Checkout được coi là hoàn thiện.

### 5.4 Product Variant/SKU

Variant là đơn vị bán thực tế:

- Mỗi Variant có identity ổn định, SKU, barcode optional và status.
- SKU canonical uppercase và unique toàn hệ thống trong single-store.
- Một Product không có hai Variant cùng tổ hợp option value.
- Một Variant chọn tối đa một value trong mỗi option.
- Mọi Variant active của cùng Product dùng cùng một tập option.
- SKU và combination là immutable; cấu hình sai được xử lý bằng deactivate Variant cũ và tạo Variant mới.
- Không hard delete Variant đã có khả năng được downstream tham chiếu.

Cart, Inventory và Order phải tham chiếu Variant/SKU, không tiếp tục dùng Product làm đơn vị bán.

### 5.5 Option/value và thuộc tính mô tả

Option/value hiện tại chỉ dành cho thuộc tính tạo Variant, ví dụ:

- `color -> red, blue`
- `size -> s, m, l`

Dictionary này do Admin cấu hình và dùng chung cho catalog. Hệ thống chỉ tạo những tổ hợp thực sự bán, không tự
sinh toàn bộ tích Descartes.

Không dùng Variant Option cho mọi thông số mô tả. Những thuộc tính như `material -> cotton` hoặc
`country_of_origin -> vietnam` không nhất thiết tạo SKU. M1 chưa xây generic attribute/EAV system. Chỉ bổ sung
descriptive attributes khi có product type và use case storefront/filter cụ thể; tránh xây một property engine
tổng quát trước nhu cầu.

### 5.6 Slug

Contract mong muốn trong single-store:

- Category slug unique toàn hệ thống và được normalize server-side.
- Product slug unique toàn hệ thống vì storefront chỉ có một catalog.
- Slug dùng lowercase ASCII, chữ số và dấu `-`; không phụ thuộc tên hiển thị sau khi đã phát hành.
- Rename không nên tự đổi slug và làm hỏng URL; thay đổi slug phải là hành động có chủ đích.
- Slug availability chỉ là hỗ trợ UX; unique constraint trong database là bảo vệ cuối cùng.

Product hiện chưa có normalization chặt như Category. Đây là một catalog follow-up hợp lệ trước khi coi contract
slug là hoàn tất.

## 6. Các module còn lại

### 6.1 Inventory

Inventory thuộc SKU, không thuộc Product.

Mô hình tối thiểu:

- `onHand`: số lượng vật lý đã ghi nhận.
- `reserved`: số lượng đang giữ cho checkout/order chưa hoàn tất.
- `available = onHand - reserved`.
- Không cho `onHand`, `reserved` hoặc `available` âm trừ khi có policy oversell được chốt rõ.
- Adjustment phải có quantity delta, reason và audit data đủ để điều tra.
- Concurrent checkout không được bán vượt tồn.

Mặc định đề xuất: Cart không reserve tồn; reservation bắt đầu khi checkout tạo Order. Reservation được consume
khi order được xác nhận theo payment policy, và được release khi order/payment hết hạn hoặc bị hủy.

### 6.2 Pricing và Promotion

Price thuộc SKU. Product có thể hiển thị price range được derive từ các SKU đang bán nhưng không phải nguồn giá
cuối cùng.

M1 nên giới hạn:

- Một currency cấu hình cho toàn cửa hàng.
- Dùng decimal money representation và rounding rule thống nhất; không dùng floating point cho money.
- SKU có base price hiện hành.
- OrderItem snapshot unit price, discount và final unit price tại checkout.
- Promotion chỉ triển khai khi có policy cụ thể; không xây rule engine tổng quát.

Nếu cần discount trong M1, ưu tiên một mô hình nhỏ, giải thích được và có thời gian hiệu lực rõ ràng. Field
`discountPercent` đơn giản trên Product không nên trở thành thiết kế cuối cùng.

### 6.3 User, Auth và Address

- Giữ hai role `USER` và `ADMIN` trong M1.
- Registration luôn tạo `USER`; không có public flow tạo `ADMIN`.
- Email phải có canonicalization và unique constraint ở database.
- Chốt policy khi LOCAL và OAuth dùng cùng email trước khi sửa account-linking.
- Customer chỉ đọc/sửa profile, address, cart và order của chính mình.
- Address book có thể thay đổi, nhưng Order phải giữ shipping-address snapshot độc lập.
- Refresh token/revocation chỉ thêm khi lifecycle phiên đăng nhập thực sự yêu cầu.

### 6.4 Cart

- Cart item được định danh bởi customer và Variant, không phải Product.
- Quantity phải dương và có giới hạn hợp lý.
- Add cùng Variant áp dụng một policy nhất quán, mặc định là tăng quantity.
- Chỉ Variant active của Product đang saleable mới được thêm.
- Cart có thể kiểm tra tồn sơ bộ nhưng không reserve inventory.
- Giá trong Cart là estimate hiện tại; checkout phải tính và xác nhận lại.
- Read-modify-write đồng thời không được làm mất quantity update.
- Customer không thể đọc hoặc mutate cart của customer khác.

### 6.5 Checkout và Order

Checkout là transaction boundary nghiệp vụ, không chỉ là thao tác copy Cart thành Order.

Luồng mục tiêu:

1. Xác thực Cart và shipping information.
2. Load/lock SKU, giá và inventory liên quan.
3. Tính lại total ở server.
4. Reserve inventory.
5. Tạo Order và OrderItem snapshot.
6. Xử lý Cart theo policy đã chốt.
7. Commit database transaction.
8. Khởi tạo payment ngoài transaction database khi phương thức thanh toán yêu cầu.

OrderItem tối thiểu snapshot:

- Product ID và Product name.
- Variant ID, SKU và mô tả option/value cần hiển thị.
- Quantity.
- Unit price, discount, final unit price và line subtotal.
- Currency.

Submit checkout phải có idempotency contract để retry không tạo hai Order. Order status chỉ đổi qua transition
hợp lệ, không set tùy ý từ controller/service khác.

### 6.6 Payment

Payment được model theo attempt/transaction đủ để một Order có thể thử thanh toán lại nếu policy cho phép.

- Backend tạo payment reference và expected amount từ Order snapshot.
- Chỉ callback/IPN đã xác thực mới được xác nhận payment thành công.
- Phải đối chiếu Order, amount, currency và provider reference.
- Callback lặp hoặc out-of-order phải idempotent.
- Không gọi provider trong một database transaction dài.
- Không log secret, signature hoặc URL/query nhạy cảm.
- Payment success/failure phải dẫn đến Order transition và inventory consume/release nhất quán.

Không cần tích hợp thêm payment provider chỉ để mở rộng công nghệ trong M1.

### 6.7 Fulfillment, Cancellation và Refund

State machine tối thiểu phải định nghĩa rõ actor và transition, ví dụ:

```text
PENDING_PAYMENT -> CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED
       |              |             |
       +--------------+-------------+-> CANCELLED
```

Tên trạng thái cuối cùng có thể điều chỉnh trong CR tương ứng, nhưng phải phân biệt payment state với fulfillment
state thay vì dùng một enum mơ hồ cho cả hai.

Cần chốt và kiểm thử:

- Customer được cancel đến trạng thái nào.
- Admin được cancel/transition trạng thái nào.
- Reservation hoặc stock được release/restock ở transition nào.
- Order đã trả tiền khi cancel sẽ đi vào refund flow nào.
- Callback fulfillment/payment lặp không tạo side effect lặp.

M1 chỉ cần flow fulfillment thủ công trong Backoffice. Chưa cần tích hợp hãng vận chuyển. Refund có thể giới hạn
toàn phần nếu partial refund chưa có acceptance criteria thực tế.

## 7. Thứ tự triển khai đề xuất

Thứ tự còn lại của M1 nên dựa trên dependency nghiệp vụ:

1. Chuẩn hóa Product slug nếu muốn đóng catalog contract trước downstream.
2. Inventory theo Variant/SKU.
3. Pricing theo Variant/SKU và money contract.
4. User/Auth/Address ownership và database invariants.
5. Cart chuyển từ Product sang Variant.
6. Checkout và Order snapshot/reservation/idempotency.
7. Payment lifecycle và callback consistency.
8. Fulfillment, Cancellation và Refund.
9. Media cleanup observability/orphan reconciliation như operational follow-up.

Mỗi mục vẫn phải tách thành CR đủ nhỏ để review. Một module lớn có thể cần nhiều CR: model/migration, write flow,
read API và concurrency behavior không bắt buộc nằm chung một thay đổi.

## 8. Nguyên tắc giữ đường mở rộng sau M1

Không thiết kế multi-vendor ngay, nhưng giữ các nguyên tắc sau để tránh tự khóa đường:

- Giữ API storefront và backoffice tách biệt; không trộn admin field vào public response.
- Giữ authorization ở use-case boundary và ownership của customer resource rõ ràng.
- Giữ Variant/SKU là đơn vị bán ổn định.
- Order luôn snapshot dữ liệu thương mại, không phụ thuộc Product mutable sau khi mua.
- Media tiếp tục đi qua storage abstraction, không để domain phụ thuộc Cloudinary.
- Không đưa giả định “chỉ có một admin” vào tên bảng hoặc business key nếu không cần thiết.
- Không tạo `store_id = 1`, `tenant_id = 1`, fake Seller entity hoặc abstraction tenant chưa có hành vi.
- Không thêm Kafka, Redis, Elasticsearch, microservice hoặc event architecture trước khi một vấn đề cụ thể yêu cầu.

Khi mở rộng multi-vendor, dự kiến sẽ cần ít nhất:

- `Store` và `StoreMembership`.
- Product/Media/Inventory/Order ownership hoặc seller partition rõ ràng.
- Seller-scoped Backoffice API và authorization theo membership.
- Product moderation/publication policy.
- SKU/slug uniqueness scope được xem xét lại.
- Tách order theo seller, shipping, commission, settlement và payout.

Đó là một thay đổi domain lớn và cần roadmap/migration riêng; không coi là cleanup nhỏ sau M1.

## 9. Ngoài phạm vi M1

- Multi-vendor marketplace và nhiều storefront theo seller.
- Seller registration/onboarding, KYC hoặc store approval.
- Commission, platform fee, settlement và payout.
- Seller staff/permission matrix.
- Multi-warehouse inventory.
- Dynamic pricing hoặc promotion rule engine tổng quát.
- Generic EAV/property engine khi chưa có use case sản phẩm cụ thể.
- Tích hợp hãng vận chuyển thật.
- Partial refund nếu chưa có yêu cầu cụ thể.
- Direct-to-Cloudinary upload nếu backend upload chưa tạo vấn đề đo được.
- Hạ tầng phân tán chỉ để mô phỏng production scale.

## 10. Definition of Done cho M1

M1 single-store được coi là hoàn thiện khi:

- Storefront chỉ expose catalog saleable với visibility nhất quán.
- Admin quản lý được toàn bộ catalog, SKU, giá và tồn kho qua Backoffice boundary.
- Customer cart và order tham chiếu SKU đúng nghĩa.
- Concurrent checkout không oversell.
- Checkout tạo snapshot giá, SKU, sản phẩm và địa chỉ đầy đủ.
- Retry checkout/payment không tạo duplicate Order hoặc side effect lặp.
- Payment callback được xác thực và đối chiếu trước khi cập nhật trạng thái.
- Order, Payment và Fulfillment có state transition rõ ràng.
- Cancellation giải phóng/restock inventory đúng policy; refund tối thiểu được xử lý nhất quán nếu nằm trong scope.
- Customer ownership và Admin authorization được bảo vệ ở API/application boundary.
- Database migration và representative tests bảo vệ các invariant quan trọng.
- Không còn downstream flow mới tiếp tục dựa vào Product-level `inStock` hoặc Product làm đơn vị bán.

Definition of Done này là mục tiêu milestone. Mỗi CR vẫn cần acceptance criteria nhỏ, cụ thể và có thể kiểm chứng;
không dùng toàn bộ danh sách trên làm scope cho một PR duy nhất.
