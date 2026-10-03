# Module Renewal Roadmap

Tài liệu này là checklist sống cho quá trình đi lại từng module trong milestone M1.
Mỗi lần audit một module, bổ sung các phát hiện đã xác minh vào đúng mục thay vì
biến chúng thành thay đổi ngoài phạm vi của CR đang làm.

Phạm vi sản phẩm của M1 đã được chốt là **single-store** với `USER` cho customer và
`ADMIN` cho người vận hành cửa hàng. Xem [M1 Single-Store Business Scope](./m1-single-store-business-scope.md)
cho các quyết định nghiệp vụ xuyên module và những nội dung chủ động để lại cho phase multi-vendor sau này.

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

Trạng thái: `[x]` lifecycle/storage boundary cơ bản, `[x]` attachment concurrency,
`[ ]` cleanup observability/orphan reconciliation.

### Đã có

- [x] `MediaPurpose` và `MediaStatus` thay cho các flag rời rạc.
- [x] Upload tạo media `TEMPORARY`; aggregate activate media khi attach.
- [x] Media API chỉ discard media `TEMPORARY`.
- [x] Cleanup xử lý media hết hạn hoặc `PENDING_DELETE` qua storage abstraction.
- [x] Brand và Category đã dùng query theo cả status và purpose khi attach.

### Follow-up đã quan sát

- [x] Mọi Brand/Category/Product attach đều khóa pessimistic Media row; transaction cạnh tranh
      chỉ được attach khi media vẫn là `TEMPORARY`, nên một upload không thể được activate cho
      hai aggregate qua application flow.
- [ ] Quyết định retry/observability cho cleanup thất bại và cách phát hiện asset
      mồ côi khi database/storage lệch nhau.
- [x] Product đã loại bỏ đường upload/delete Cloudinary cũ; create/update/delete dùng
      Media lifecycle và `ProductImage` chỉ tham chiếu Media.

## 1. Engineering baseline và security containment

Đây là gate trước các CR nghiệp vụ tiếp theo, không phải một đợt redesign.

### Trạng thái đã xác minh

- [x] Maven wrapper chạy được các lệnh verification tại local và CI.
- [x] `pom.xml` và CI cùng dùng Java 25 làm baseline build.
- [x] `SecurityConfig` dùng public allowlist và `.anyRequest().authenticated()`; catalog/media
      write cùng Order administration yêu cầu ADMIN tại HTTP boundary.

### Exit criteria

- [x] Wrapper chạy được `spotless:check` và `test` trên cùng JDK với CI.
- [x] Có test production security chain cho public, authenticated và admin endpoint;
      authorization fallback `permitAll` đã được loại bỏ.
- [x] Baseline không thêm framework/dependency ngoài nhu cầu verification cụ thể.

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

## 3. Brand - đã hoàn tất vòng đầu

Trạng thái: `[x]` model/admin API boundaries (CR1), `[x]` write invariants, concurrency
và delete safety (CR2), `[x]` storefront read model (CR3).

### Điểm cần audit

- [x] Chốt quan hệ Brand-Category: association là optional; category được gắn phải
      effectively active; create/update dùng semantics null/empty/replace đã thống nhất.
- [x] Tách public/admin query và thay `publicFlg` bằng `BrandStatus.ACTIVE/INACTIVE`.
- [x] Chốt tên Brand được trim và unique không phân biệt hoa/thường.
- [x] Kiểm tra delete brand đang được Product tham chiếu; không dựa riêng vào lỗi FK
      để biểu diễn nghiệp vụ.
- [x] Giữ attachment Media theo `BRAND_LOGO` với lifecycle đúng khi attach/replace/remove.
      Media row được khóa khi attach nên một upload không thể được activate cho hai aggregate
      qua application flow.

### Test nhỏ nhất có ý nghĩa

- [x] Create/update với category hợp lệ và logo đúng purpose.
- [x] Từ chối category/media không hợp lệ.
- [x] Từ chối xóa brand đang được Product sử dụng.
- [x] Storefront chỉ trả Brand active, hỗ trợ lọc theo Category effectively active và
      giữ admin boundary được bảo vệ.

## 4. Product catalog và tích hợp Media - đã hoàn tất vòng đầu

Trạng thái: `[x]` Media attachment/API contract, `[x]` ordered image lifecycle,
`[x]` schema migration và persistence boundary.

### Đã hoàn tất

- [x] `ProductCreateRequest`/`ProductUpdateRequest` nhận ordered `imageIds`, giới hạn tối đa
      5 ảnh và từ chối ID trùng.
- [x] `ProductImage` tham chiếu `Media`, lưu `displayOrder`; ảnh thứ tự 0 là main image.
- [x] `ProductServiceImpl` dùng Media lifecycle khi attach/replace/delete, không upload hoặc
      delete Cloudinary trực tiếp.
- [x] `ProductMapper` dựng URL từ Media đã attach, không phụ thuộc `CloudinaryUploader`.
- [x] Changeset 9 xóa `products.main_image`/`product_images.url`, thêm `media_id`,
      `display_order`, unique constraint và FK phù hợp.
- [x] Có service, persistence và MySQL migration test đại diện cho Product image flow.

### Quyết định đã chốt

- [x] Main image là image có `displayOrder = 0`; không giữ cờ hoặc field riêng trên Product.
- [x] Update images replace toàn bộ ordered list; media bị bỏ được chuyển `PENDING_DELETE`.
- [x] Mỗi Product có tối đa 5 ảnh; request trùng media ID bị từ chối và mỗi Media chỉ được
      attach vào một `ProductImage` theo unique constraint.
- [x] Create/update yêu cầu Category effectively active và Brand active; Product đã được
      tham chiếu bởi Order không được xóa.
- [x] Không backfill URL legacy: migration xóa dữ liệu `product_images` cũ trước khi đổi sang
      Media identity.

### Test nhỏ nhất có ý nghĩa

- [x] Create product attach ordered temporary media đúng purpose.
- [x] Từ chối media không hợp lệ trước khi mutate aggregate; duplicate ID bị chặn ở request
      validation và database bảo vệ ownership.
- [x] Update remove/reorder images, đánh dấu media bỏ đi là `PENDING_DELETE`.
- [x] Delete product chuyển media liên quan sang cleanup flow; từ chối delete nếu OrderItem
      đang tham chiếu và giữ nguyên media.
- [x] Migration test xác minh bỏ schema URL legacy, thêm Media attachment và bảo vệ FK Order.

### Follow-up không chặn Inventory

- [ ] Chuẩn hóa Product slug theo cùng nguyên tắc lowercase ASCII, chữ số và dấu `-` như
      contract single-store đã chốt; rename không tự đổi slug và database vẫn bảo vệ uniqueness.

## 5. Product API boundary - đã hoàn tất

### Đã hoàn tất

- [x] Tách `/products/**` thành storefront read-only và `/admin/products/**` thành Product administration với class-level ADMIN authorization.
- [x] Storefront list/detail/convenience/facet dùng cùng visibility rule: Product published, Brand active, Category effectively active và có active Variant.
- [x] Tách filter/response DTO giữa storefront và admin; storefront không nhận `hasPublished` và không expose `published`.
- [x] Create luôn tạo draft; metadata update không đổi publication; publish/unpublish là operation riêng có validation Category, Brand và active Variant.
- [x] Persistence test bảo vệ ancestor visibility, hidden slug detail và pagination distinct khi Product có nhiều Variant.

### Compatibility

- [x] Các route write/read admin cũ dưới `/products` đã được thay bằng resource-oriented `/admin/products` routes; không giữ compatibility alias trong CR này.

## 6. Product Variant/SKU - đã hoàn tất vòng đầu

Trạng thái: `[x]` option dictionary (CR1), `[x]` Variant/SKU foundation (CR2),
`[x]` storefront selection và attribute filtering (CR3).

### Quyết định đã chốt

- [x] Option/value là catalog dictionary dùng chung; admin tự cấu hình thuộc tính và chỉ tạo
      các tổ hợp thực sự bán, không hard-code size/color hoặc sinh tích Descartes.
- [x] SKU canonical uppercase và unique toàn hệ thống; barcode optional/unique; Variant có
      trạng thái `ACTIVE/INACTIVE`; ảnh vẫn thuộc Product trong vòng này.
- [x] Giá tạm thời vẫn thuộc Product và được để lại cho Pricing CR; tồn kho đã chuyển sang
      Variant/Inventory, còn Product chỉ derive trạng thái `inStock` để phục vụ storefront.
- [x] SKU và tổ hợp option là immutable, không có hard delete; cấu hình sai được xử lý bằng
      deactivate Variant cũ và tạo Variant mới.

### Đã hoàn tất

- [x] Admin quản lý option/value và batch-create/list/update barcode/status cho Variant.
- [x] Product chỉ publish khi có active Variant; Product row được lock trong write flow và
      không thể deactivate active Variant cuối cùng của Product published.
- [x] Product detail trả ordered active option/value matrix và active Variant để client xác
      định `variantId`; inactive Variant/value không bị expose.
- [x] Product filter hỗ trợ OR trong cùng option, AND giữa các option và bắt buộc cùng một
      active Variant; pagination giữ Product distinct.
- [x] Storefront facet `/products/filter-options` chỉ trả option/value active có trong active
      Variant của Product published theo category/brand filter.

### Exit criteria

- [x] SKU/Variant là identity bán ổn định để Cart, Inventory và Order tham chiếu ở CR sau.
- [x] Database bảo vệ unique SKU/barcode/tổ hợp, một value mỗi option, value thuộc đúng option
      và có index cho Product/status cùng reverse lookup option value.
- [x] Có service, persistence, concurrency và MySQL migration test cho option matrix, duplicate
      SKU/tổ hợp, publish/deactivate invariant, storefront read/filter/facet và N+1 boundary.

## 7. Inventory - đã hoàn tất vòng đầu

Trạng thái: `[x]` SKU inventory foundation và admin adjustment (CR1, issue #52),
`[x]` storefront availability và loại bỏ mutable `Product.inStock` (CR2, issue #53).

Inventory foundation đã được hoàn thiện qua hai CR độc lập. CR2 dùng schema và application
boundary đã merge từ CR1; reservation lifecycle vẫn thuộc Checkout/Order/Payment ở vòng sau.

### Quyết định đã chốt

- [x] Inventory thuộc Product Variant/SKU, không thuộc Product.
- [x] Lưu `onHand` và `reserved`; `available = onHand - reserved` là giá trị derive, không lưu
      thành nguồn dữ liệu thứ ba có thể lệch.
- [x] Không cho `onHand`, `reserved` hoặc `available` âm; M1 không hỗ trợ oversell.
- [x] Admin thay đổi tồn bằng adjustment có quantity delta, reason và audit data thay vì set
      số lượng không để lại dấu vết.
- [x] Cart chỉ kiểm tra tồn sơ bộ và không reserve; reservation bắt đầu trong Checkout.
- [x] Reservation có timeout 15 phút mặc định; callback hợp lệ consume tồn đã reserve,
      còn cleanup hết hạn release reservation theo Order lock.

### CR1 - SKU inventory foundation và admin adjustment

- [x] Tạo một Inventory balance cho mỗi Variant với `onHand = 0`, `reserved = 0`; backfill các
      Variant hiện có và tạo balance cho Variant mới.
- [x] Database bảo vệ non-negative balance và `reserved <= onHand`.
- [x] Backoffice đọc tồn theo SKU và tạo adjustment với delta/reason.
- [x] Từ chối adjustment làm `onHand < reserved` hoặc làm balance âm.
- [x] Chống lost update khi hai adjustment cùng tác động một SKU.
- [x] Không expose reserve/release qua HTTP và chưa tích hợp Order trong CR này.

### CR2 - Storefront availability

- [x] Variant storefront trả availability cần để client xác định SKU còn bán được.
- [x] Product được coi là in stock khi có ít nhất một Variant active với `available > 0`.
- [x] Product hết hàng vẫn có thể visible dưới dạng sold out; chỉ filter `inStock=true` loại nó.
- [x] List, detail và stock filter dùng cùng inventory semantics mà không làm sai pagination/count.
- [x] Loại bỏ mutable Product-level `inStock` khỏi entity, write request, legacy response và
      schema; storefront response/filter giữ `inStock` dưới dạng giá trị derive từ Inventory.

### Test nhỏ nhất có ý nghĩa

- [x] CR1: adjustment hợp lệ cập nhật `onHand` và tính đúng `available`.
- [x] CR1: từ chối adjustment làm balance âm hoặc thấp hơn `reserved`.
- [x] CR1: hai adjustment cạnh tranh không làm mất update; migration backfill đúng balance zero.
- [x] CR2: storefront trả availability theo active SKU và Product stock filter dùng cùng semantics.
- Case hai checkout cạnh tranh không oversell và release khi thất bại/hết hạn thuộc
  Checkout/Order integration, không bị kéo sớm vào hai CR Inventory này.

## 8. Pricing và Promotion - đã hoàn tất vòng đầu

Trạng thái: `[x]` SKU base price (CR1, issue #56), `[x]` storefront pricing và OrderItem
snapshot (CR2, issue #57), `[x]` scheduled SKU sale (CR3, issue #58).

### Quyết định đã chốt

- [x] Giá thuộc SKU; Product chỉ có thể derive price/range để hiển thị và không phải nguồn giá
      cuối cùng khi checkout.
- [x] M1 dùng một currency cấu hình cho toàn cửa hàng và decimal money representation với
      rounding/range constraint thống nhất; không dùng floating point cho money.
- [x] OrderItem snapshot unit price, discount, final unit price, subtotal và currency.
- [x] Không xây promotion rule engine tổng quát trong M1.

### Đã hoàn tất

- [x] Chuyển base price từ Product sang Variant/SKU, thêm admin price API và loại bỏ
      `Product.price`/`discountPercent` khỏi domain, API và schema.
- [x] Storefront derive effective price/range từ các SKU đang bán; Cart hiển thị estimate hiện tại
      và OrderItem snapshot base price, discount amount, final unit price, subtotal cùng currency.
- [x] Dùng money `DECIMAL(15,2)` với range/scale constraint và một store currency `VND` thống nhất
      với payment provider hiện tại.
- [x] Triển khai promotion nhỏ theo SKU dưới dạng scheduled sale gồm `salePrice`, `saleStartsAt` và
      `saleEndsAt`; giá sale chỉ có hiệu lực trong khoảng thời gian đã cấu hình.
- [x] Không xây promotion rule engine tổng quát; Product-level `discountPercent` đã được thay bằng
      discount amount derive từ scheduled SKU sale.
- [x] Có service, domain, storefront persistence, API/security và MySQL migration test cho base
      price, effective sale price, price filtering/range và OrderItem snapshot.

Scheduled SKU sale là promotion model duy nhất của M1; các rule tổng quát, stacking, coupon và
campaign phức tạp được để lại cho phase sau khi có acceptance criteria cụ thể.

## 9. User/Auth/Address - đã hoàn tất vòng đầu

Trạng thái: `[x]` canonical LOCAL email identity (CR1, issue #62),
`[x]` rotating LOCAL refresh session (CR2, issue #63),
`[x]` customer profile và address book (CR3, issue #64).

### Quyết định phạm vi

- [x] M1 giữ hai role `USER` và `ADMIN`; không thêm `SELLER`, Store hoặc tenant abstraction.
- [x] Registration công khai chỉ tạo `USER`; Admin là người vận hành toàn bộ single-store.
- [x] Address profile có thể thay đổi nhưng Order luôn giữ shipping-address snapshot độc lập.

### Đã hoàn tất

- [x] Issue #62 chuẩn hóa email LOCAL bằng trim và lowercase `Locale.ROOT` trước validation,
      registration, login và các customer identity lookup.
- [x] Public registration luôn tạo `USER/LOCAL`; password authentication chỉ resolve LOCAL,
      còn email đã thuộc OAuth account bị xử lý như invalid credentials.
- [x] `users.email` có unique constraint toàn cục `uk_users_email`; migration guard phát hiện
      canonical collision trước khi update và giữ nguyên provider/role, không merge hoặc xóa user.
- [x] Issue #63 bổ sung rotating refresh session cho LOCAL: access token ngắn hạn, cookie HttpOnly host-only,
      row lock, one-time rotation, reuse revocation và logout idempotent; OAuth vẫn giữ flow cũ.
- [x] Issue #64 bổ sung self-profile update và address book có cấu trúc; mọi address lookup đều
      scope theo principal hiện tại, cross-owner miss trả cùng `RESOURCE_NOT_FOUND` như row không tồn tại.
- [x] Customer API hiện tại lấy identity từ authenticated principal; các API vận hành catalog,
      inventory, media và Order administration yêu cầu role `ADMIN`.

### Test nhỏ nhất có ý nghĩa

- [x] Register/login canonical email, duplicate pre-check và uniqueness race translation.
- [x] LOCAL-only authentication boundary, customer lookup compatibility, concurrency và MySQL
      migration coverage cho canonical email/uniqueness.
- [x] LOCAL session issue/rotate/revoke, generic refresh failure, cookie/header security boundary, JWT issuer
      validation và MySQL migration/locking coverage.
- [x] Profile/address API authentication, input normalization, address ownership isolation và
      MySQL migration/FK/cascade coverage.
- [x] Customer không truy cập address của customer khác; representative admin boundary test hoạt động.

### Follow-up không chặn Cart

- [ ] Chốt và kiểm thử riêng account linking/unlinking LOCAL/OAuth nếu M1 cần thay đổi social-login flow;
      ba CR trên chủ động giữ nội dung này ngoài phạm vi.

## 10. Cart - đã hoàn tất vòng đầu

Trạng thái: `[x]` customer Cart theo Variant/SKU, `[x]` guest Cart có credential
cookie và claim/merge an toàn.

### Quyết định đã chốt

- [x] Cart item được định danh bởi customer và Variant/SKU, không phải Product.
- [x] Add cùng Variant mặc định tăng quantity theo policy được bảo vệ trong transaction.
- [x] Cart không reserve inventory; chỉ kiểm tra sơ bộ và Checkout kiểm tra/reserve lại.
- [x] Giá trong Cart là estimate hiện tại, không phải giá cuối cùng của Order.
- [x] Guest Cart dùng credential opaque được hash trong database, cookie HttpOnly/Secure/SameSite,
      expiry/renewal, guard header cho mutation và claim yêu cầu authenticated user.
- [x] Guest claim khóa deterministic theo Cart ID, merge duplicate Variant không vượt quá 99,
      chuyển guest không có customer Cart, xóa guest Cart và expire cookie thành công.
- [x] Cart read giữ item nhưng trả trạng thái `UNAVAILABLE`/`INSUFFICIENT_STOCK` theo inventory hiện tại.
- [x] Guest Cart hết hạn được cleanup theo batch bằng scheduler.

### Test nhỏ nhất có ý nghĩa

- [x] Add cùng SKU tăng quantity theo policy và concurrent update không mất quantity.
- [x] Từ chối quantity/SKU không hợp lệ; guest credential malformed/expired/forged bị xử lý generic.
- [x] Customer isolation, guest cookie guard, public Cart routes và authenticated claim boundary.
- [x] Claim merge/lock behavior, migration/persistence mapping và API contract test.

## 11. Checkout và Order - đã hoàn tất vòng đầu

Trạng thái: `[x]` atomic Checkout và submit idempotency (Slice 1),
`[x]` reservation completion/expiry (Slice 2), `[x]` Order transition boundary (Slice 3).

### Đã hoàn tất

- [x] `POST /checkout` lấy customer từ principal, address theo ownership và Cart hiện tại;
      route tạo Order legacy đã được loại bỏ.
- [x] Checkout khóa theo thứ tự ổn định, kiểm tra lại catalog/giá/tồn, reserve Inventory,
      tạo Order cùng immutable OrderItem/shipping snapshot và xóa Cart trong một transaction.
- [x] Submit dùng `Idempotency-Key` đã hash cùng request fingerprint; retry hợp lệ trả lại
      Order cũ mà không reserve hoặc tạo Order lần hai.
- [x] Khởi tạo VNPay chạy sau khi Checkout transaction commit; lỗi provider giữ Order có thể
      retry và reservation được cleanup khi hết hạn.
- [x] Callback VNPay đã xác thực consume reservation và confirm Order đúng một lần; cleanup
      release reservation và chuyển Order sang `PAYMENT_EXPIRED`, cùng khóa Order để phân xử race.
- [x] Order chỉ đi qua transition hợp lệ `PENDING_PAYMENT -> CONFIRMED/PAYMENT_EXPIRED ->`
      `PROCESSING -> SHIPPED -> DELIVERED`; admin chỉ điều khiển forward fulfillment transition.
- [x] Đã loại bỏ application method đánh dấu Payment `PAID` trực tiếp; customer/admin read DTO
      dùng OrderItem và shipping snapshot thay vì dữ liệu catalog/address hiện tại.

### Test nhỏ nhất có ý nghĩa

- [x] Checkout snapshot đúng giá/SKU/address, reserve tồn và xóa Cart theo policy.
- [x] Thiếu tồn hoặc callback consume lỗi rollback nhất quán; hai customer không oversell.
- [x] Callback và expiry cạnh tranh chỉ finalize reservation một lần.
- [x] Transition không hợp lệ bị từ chối; full fulfillment chain chỉ gán `deliveredAt` ở bước cuối.
- [x] Retry cùng idempotency key không tạo duplicate Order hoặc reservation.

## 12. Payment

Trạng thái: `[x]` verified VNPay callback nối với Order/Inventory lifecycle,
`[ ]` payment attempt/retry model và các hardening follow-up.

### Vấn đề đã quan sát

- [x] VNPay callback/IPN đã nối với Payment, Order và Inventory trong một transaction:
      callback thành công consume reservation, đánh dấu `PAID` và confirm Order.
- [x] Đã loại bỏ `OrderServiceImpl.payment()`; chỉ callback đã xác thực và đối chiếu
      amount/order reference được phép hoàn tất Payment.
- [x] Callback lặp được xử lý idempotent và không consume Inventory lần hai; callback đến sau
      expiry không thể reopen Order.
- [ ] Tách payment attempt/transaction nếu một order có thể thanh toán lại.
- [ ] Không log URL/query chứa dữ liệu nhạy cảm nếu provider đưa signature/token vào đó.

### Test nhỏ nhất có ý nghĩa

- [ ] Init payment đúng amount và reference.
- [x] IPN signature sai bị từ chối.
- [x] Callback hợp lệ cập nhật đúng một lần; callback lặp không gây side effect lặp.
- [x] Amount/reference không khớp không được đánh dấu paid.

## 13. Fulfillment, Cancellation và Refund

Đây là phần sau cùng vì phụ thuộc Order, Inventory và Payment lifecycle.

### Quyết định phạm vi

- [x] M1 chỉ cần fulfillment thủ công qua Backoffice; chưa tích hợp hãng vận chuyển.
- [x] Partial refund nằm ngoài M1 nếu chưa có acceptance criteria cụ thể; ưu tiên full refund
      gắn với cancellation hợp lệ.

### Câu hỏi nghiệp vụ còn lại

- [x] Order sau thanh toán đi `CONFIRMED -> PROCESSING -> SHIPPED -> DELIVERED`;
      chỉ ADMIN được thực hiện forward fulfillment transition.
- [ ] Customer/admin được cancel ở trạng thái nào; tồn kho được release/restock khi nào.
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
