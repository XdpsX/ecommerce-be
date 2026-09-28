# Product Variant/SKU Renewal Plan

## 1. Mục tiêu

Hoàn thiện mục 5 của `module-renewal-roadmap.md` để:

- Một Product có thể có nhiều Variant/SKU.
- Người dùng chọn các option như màu sắc và kích thước để xác định đúng một Variant.
- Tìm kiếm Product có thể lọc theo option value.
- SKU trở thành identity ổn định để Inventory, Cart và Order tham chiếu ở các CR sau.

Phần này chỉ xây nền catalog cho Variant/SKU. Không kéo Inventory, Pricing, Cart hoặc
Order vào cùng thay đổi.

## 2. Hiện trạng đã xác minh

- Chưa có entity, bảng, repository hoặc API cho Variant/SKU và option.
- `Product` đang giữ trực tiếp `price`, `discountPercent`, `inStock` và `published`.
- Product filter hiện chỉ hỗ trợ search, publish, price, discount, stock, category và brand.
- Cart dùng khóa `(user_id, product_id)` và OrderItem cũng tham chiếu trực tiếp Product.
- Product detail chưa có dữ liệu để client dựng bộ chọn option hoặc xác định Variant.
- Repository đang dùng MySQL, JPA Specification và Liquibase; chưa cần thêm dependency hay
  search infrastructure cho yêu cầu này.

## 3. Quyết định thiết kế

### 3.1 Option dùng chung trong catalog

Option và option value có identity dùng chung giữa nhiều Product, thay vì lưu chuỗi tự do
riêng trên từng Variant.

Ví dụ:

- Option `color`: Black, White, Red.
- Option `size`: S, M, L.

Cách này cho phép `Black` có cùng identity trên nhiều Product, nên storefront có thể filter
toàn catalog hoặc một category bằng option value ID.

Mỗi option/value gồm:

- ID nội bộ.
- `code` ổn định, unique và được canonicalize, dùng cho identity kỹ thuật.
- `name` hiển thị, có thể sửa mà không đổi identity.
- `displayOrder`.
- Trạng thái `ACTIVE` hoặc `INACTIVE`.

Không hard-code các option như size hoặc color trong Java. Không cho xóa cứng option/value;
chỉ deactivate. Từ chối deactivate khi value đang được Variant active sử dụng.

### 3.2 Variant và tổ hợp option

`ProductVariant` là đơn vị bán ổn định và gồm:

- `id`.
- `productId`.
- `sku` bắt buộc, trim và canonicalize thành uppercase, unique toàn hệ thống.
- `barcode` optional, unique khi có giá trị.
- Trạng thái `ACTIVE` hoặc `INACTIVE`.
- `combinationKey` nội bộ, sinh từ các cặp option/value đã sắp xếp theo option ID.
- Audit timestamps.

Một Variant chọn tối đa một value cho mỗi option. Database phải bảo vệ quan hệ value thuộc
đúng option; không chỉ dựa vào validation ở service.

Các Variant active của cùng Product phải sử dụng cùng tập option. Không bắt buộc sinh toàn bộ
tích Descartes: admin chỉ tạo các tổ hợp thực sự bán.

Ví dụ Product chỉ có `Black/M` và `White/L` là hợp lệ. Tuy nhiên filter `Black + L` không được
trả Product đó vì không có một Variant active nào đồng thời khớp cả hai lựa chọn.

Product đơn giản không có option vẫn có một Variant với tổ hợp rỗng. Unique constraint trên
`(product_id, combination_key)` đảm bảo chỉ có một Variant mặc định như vậy.

### 3.3 Vòng đời

- Product draft có thể chưa có Variant để hỗ trợ workflow cấu hình từng bước.
- Product chỉ được publish khi có ít nhất một Variant `ACTIVE`.
- Không được deactivate Variant active cuối cùng của một Product đang published.
- Không có API hard delete Variant.
- SKU và tổ hợp option không sửa sau khi tạo. Nếu cấu hình sai, deactivate Variant cũ và tạo
  Variant mới.
- Barcode có thể cập nhật; status được thay đổi bằng operation riêng.
- Ghi Variant và publish Product phải lock theo Product để hai transaction không đồng thời
  phá invariant "published Product có active Variant".

Chính sách không hard delete giúp SKU giữ identity ổn định trước khi Cart và Order bắt đầu
tham chiếu nó. Khi Order được renewal, FK và snapshot sẽ bổ sung bảo vệ lịch sử ở downstream.

### 3.4 Giá, tồn kho và ảnh

- Giữ `Product.price` và `discountPercent` trong CR này; chuyển giá về SKU ở mục 7 - Pricing.
- Giữ `Product.inStock` tạm thời; thay bằng inventory theo SKU ở mục 6 - Inventory.
- Ảnh tiếp tục thuộc Product. Variant-specific images là follow-up nếu có use case cụ thể.
- Trạng thái Variant không biểu diễn còn hàng. `ACTIVE` chỉ có nghĩa Variant đang được phép bán
  nếu Product và các rule downstream cũng hợp lệ.

### 3.5 Semantics filter

Storefront nhận danh sách `optionValueIds`.

- Nhiều value thuộc cùng option dùng OR, ví dụ `Black OR White`.
- Các option khác nhau dùng AND, ví dụ `(Black OR White) AND Size M`.
- Tất cả điều kiện phải khớp trên cùng một Variant `ACTIVE`.
- Product kết quả phải distinct để pagination và total count không bị nhân theo số Variant.
- ID không tồn tại hoặc inactive được trả lỗi validation rõ ràng, không âm thầm bỏ qua.

Ví dụ:

```text
GET /products?categoryId=10&optionValueIds=101,102,205
```

Nếu `101/102` thuộc Color và `205` thuộc Size thì query có nghĩa:

```text
(Color = 101 OR Color = 102) AND Size = 205
```

SQL/JPA query phải group trên cùng Variant và yêu cầu số option group khớp bằng số option được
chọn. Không được kiểm tra từng value bằng các Variant khác nhau của cùng Product.

## 4. Phạm vi CR đề xuất

Mục 5 nên được triển khai qua ba CR độc lập để migration, invariant và query có thể review rõ.

## CR1 - Catalog option dictionary

### Mục tiêu

Tạo từ điển option/value dùng chung để Variant và product filter có identity ổn định.

### Thay đổi

1. Thêm domain và persistence:
   - `VariantOption`.
   - `VariantOptionValue`.
   - `VariantOptionStatus` hoặc status dùng chung phù hợp.
   - Repository cho option và value.
2. Thêm admin API dưới `/admin/variant-options`:
   - List/detail option và values.
   - Create option.
   - Update display metadata/order.
   - Add/update/deactivate value.
   - Deactivate option khi không có active Variant sử dụng.
     CR1 chưa có bảng Variant để kiểm tra reference, nên từ chối chuyển `ACTIVE -> INACTIVE`; CR2 sẽ mở lại operation khi bổ sung guard trên active Variant.
3. Canonicalize code bằng trim + lowercase; name được trim nhưng giữ cách hiển thị.
4. Bắt `DataIntegrityViolationException` để DB unique race trả lỗi ứng dụng ổn định.
5. Bổ sung OpenAPI contract cho endpoint mới.

### Database

Tạo changeset mới với:

- `variant_options`.
- `variant_option_values`.
- Unique `variant_options.code`.
- Unique `(option_id, code)` và `(option_id, display_order)` cho value.
- Index phục vụ đọc active values theo option/order.

Không seed cố định Color/Size; admin tự cấu hình catalog data.

### Test đại diện

- Tạo option cùng ordered values và đọc lại đúng thứ tự.
- Từ chối code option/value trùng sau canonicalization.
- Persistence test chứng minh unique constraint vẫn bảo vệ concurrent/race boundary.

## CR2 - Product Variant/SKU foundation

### Mục tiêu

Thêm Variant vào Product, quản lý tổ hợp option và bảo vệ lifecycle của SKU.

### Thay đổi

1. Thêm:
   - `ProductVariant`.
   - `ProductVariantStatus`.
   - Quan hệ Variant với selected option values.
   - Repository và service cho Variant.
2. Thêm admin API dưới `/admin/products/{productId}/variants`:
   - List tất cả Variant của Product.
   - Batch-create một hoặc nhiều Variant trong cùng transaction.
   - Update barcode.
   - Activate/deactivate Variant.
   - Không có delete endpoint.
3. Batch-create nhận mỗi Variant gồm `sku`, optional `barcode`, và `optionValueIds`.
4. Validate:
   - Option/value phải active và value thuộc đúng option.
   - Một Variant không chọn hai value của cùng option.
   - Không trùng SKU, barcode hoặc tổ hợp trong request/database.
   - Các active Variant của Product dùng cùng tập option.
5. Thêm quan hệ `variants` vào Product nhưng không đưa toàn bộ collection vào Product list.
6. Sửa publish flow: Product cần ít nhất một active Variant.
7. Lock Product row trong các write operation ảnh hưởng Variant/publish để serialize invariant
   theo từng Product.
8. Admin response hiển thị cả Variant active và inactive cùng selected option values.

### Database

Tạo changeset mới với:

- `product_variants`.
- `product_variant_selections`.
- Unique toàn cục cho canonical SKU.
- Unique nullable cho barcode.
- Unique `(product_id, combination_key)`.
- Primary/unique constraint bảo đảm mỗi Variant chỉ chọn một value cho một option.
- Composite FK bảo đảm `option_value_id` thực sự thuộc `option_id` đã lưu.
- Index `(product_id, status)` và reverse index từ option value đến Variant.
- FK Product -> Variant dùng `ON DELETE CASCADE`; các reference downstream sau này sẽ dùng
  `RESTRICT` hoặc snapshot theo thiết kế của Cart/Order.

Theo xác nhận hiện tại không có dữ liệu cũ cần giữ, nên không backfill SKU giả. Nếu database
development đã phát sinh Product, reset dữ liệu trước migration thay vì tạo identity legacy
không có ý nghĩa nghiệp vụ.

### Test đại diện

- Batch-create ma trận `Black/M`, `Black/L`, `White/M`, `White/L` thành công.
- Từ chối duplicate SKU hoặc duplicate combination và rollback toàn batch.
- Từ chối hai values của cùng option hoặc tập option không nhất quán.
- Publish yêu cầu active Variant; không deactivate Variant active cuối cùng của Product
  published.
- Persistence test cho unique/composite FK, bao gồm concurrent duplicate SKU boundary.

## CR3 - Storefront variant selection and attribute filtering

### Mục tiêu

Cho client dựng option selector, xác định Variant và filter Product theo option values.

### Thay đổi

1. Mở rộng Product detail storefront bằng read model riêng:
   - Danh sách option đang được Product sử dụng, theo `displayOrder`.
   - Các active value khả dụng cho từng option.
   - Danh sách active Variant gồm `variantId`, `sku` và `optionValueIds`.
   - Không expose inactive Variant.
2. Client xác định Variant từ tập option value đã chọn bằng ma trận trả trong Product detail.
   Cart sau này gửi `variantId`; server vẫn phải validate lại khi add-to-cart.
3. Mở rộng `ProductParams` với `List<Long> optionValueIds` và validation duplicate/size.
4. Thêm filter Specification hoặc custom repository query với semantics:
   - OR trong cùng option.
   - AND giữa các option.
   - Cùng một active Variant phải khớp toàn bộ option group.
   - `distinct` Product và count query đúng khi phân trang.
5. Thêm storefront facet endpoint, ví dụ:
   - `GET /products/filter-options?categoryId=...&brandId=...`
   - Chỉ trả option/value active thực sự xuất hiện trong active Variant của tập Product phù hợp.
   - Chưa tính facet count trong M1; chỉ trả danh sách lựa chọn khả dụng.
6. Batch-fetch Variants/options cho Product detail hoặc page khi cần; tránh N+1.
7. Cập nhật OpenAPI documentation và contract tests.

### Test đại diện

- Product detail tạo được option selector và chỉ expose active Variant/value.
- Filter một value trả đúng Product.
- Filter nhiều values cùng option dùng OR, nhiều option dùng AND.
- Learning boundary: Product có `Black/M` và `White/L` không match `Black + L`.
- Pagination không duplicate Product và total count đúng khi nhiều Variant cùng match.
- Query-count/persistence test bảo vệ khỏi N+1 trên read flow đã chọn.

## 5. Luồng dữ liệu sau khi hoàn tất

```text
Admin tạo Option/Value
        |
        v
Admin batch-create Product Variants + optionValueIds
        |
        v
Product detail trả option matrix + active variants
        |
        +--> Người dùng chọn optionValueIds --> client xác định variantId
        |
        `--> Product search group option values --> tìm Product có cùng một active Variant khớp
```

## 6. Các component dự kiến bị ảnh hưởng

- `catalog/product/api`: admin Variant endpoints, Product detail/filter DTO.
- `catalog/product/application`: Variant write service, publish invariant, storefront mapping.
- `catalog/product/domain`: ProductVariant và quan hệ selection.
- `catalog/product/persistence`: repositories, Product Specification/custom query.
- Một package option trong catalog cho option/value API, application, domain và persistence.
- Liquibase changesets mới; không sửa changeset đã áp dụng.
- OpenAPI tests, service tests và persistence/migration tests tương ứng.

Tên package cụ thể có thể được chốt lúc implement, nhưng option phải nằm trong catalog boundary
và không được đưa sang Inventory/Pricing.

## 7. Ngoài phạm vi

- Inventory quantity/reservation/availability theo SKU.
- Chuyển giá hoặc promotion từ Product sang SKU.
- Chuyển Cart từ `product_id` sang `variant_id`.
- OrderItem snapshot SKU, option, tên và giá.
- Variant-specific images.
- Facet count, Elasticsearch hoặc search engine mới.
- Sinh tự động toàn bộ tích Descartes của option values.
- Hard delete option, option value hoặc Variant.
- Redesign toàn bộ Product admin/storefront API ngoài phần cần cho Variant read/filter.

## 8. Verification

Cho từng CR:

1. Chạy focused service và persistence tests của CR.
2. Chạy migration test bằng MySQL Testcontainers khi schema/constraint thay đổi.
3. Chạy `./mvnw.cmd spotless:check`.
4. Chạy `./mvnw.cmd verify`.
5. Kiểm tra diff so với `phase/m1-ecommerce-renewal` và bảo đảm không kéo Cart, Inventory,
   Pricing hoặc Order vào ngoài phạm vi.

## 9. Rủi ro và điểm cần giữ khi implement

- Filter sai nếu các selected values được match trên nhiều Variant khác nhau của cùng Product;
  persistence test `Black/M + White/L` phải bảo vệ boundary này.
- Service-level uniqueness không đủ dưới concurrency; database constraints vẫn là nguồn bảo vệ
  cuối cùng.
- Hai request deactivate/publish đồng thời có thể làm Product published không còn active Variant;
  write flow phải lock theo Product.
- Join Variant/options có thể làm duplicate Product và sai page count; query phải distinct và có
  integration test với dữ liệu nhiều Variant.
- Tải toàn bộ Variant trong Product page có thể gây payload lớn/N+1; list chỉ tải dữ liệu thật sự
  cần, detail mới trả variant matrix.
- Option/value được dùng làm filter identity nên code không được thay đổi tùy tiện; đổi label
  không được làm thay đổi ID hoặc kết quả filter.

## 10. Điều kiện hoàn tất mục 5

- Option/value có identity dùng chung và lifecycle rõ ràng.
- Product quản lý được nhiều SKU với tổ hợp option hợp lệ.
- Product published luôn có ít nhất một active SKU.
- Storefront có đủ dữ liệu để người dùng chọn option và xác định Variant.
- Product filter theo option values đúng OR/AND semantics trên cùng một active Variant.
- SKU, barcode và combination được bảo vệ bằng database constraint/index.
- SKU/Variant không bị hard delete và sẵn sàng để Inventory, Pricing, Cart và Order tham chiếu.
