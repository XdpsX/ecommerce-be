# CR — Standardize API Error Handling — HANDOFF (dừng giữa chừng)

Ngày dừng: 2026-09-24
Nhánh hiện tại: `phase/m1-ecommerce-renewal` (clean trước khi bắt đầu, chỉ có `docs/cr-standardize-api-error-handling-plan.md` đã stage)
Kế hoạch gốc: `docs/cr-standardize-api-error-handling-plan.md`

> Trạng thái: **CHƯA compile được.** Đang migrate dở. Xem mục "Việc còn lại" bên dưới.

---

## 1. Đã làm xong (ĐÃ áp dụng vào working tree)

### File MỚI đã tạo (`src/main/java/com/xdpsx/ecommerce/common/error/`)

- **`ErrorCode.java`** — enum thuần, không phụ thuộc Spring/HttpStatus. Mỗi code có `title()` + `detail()` controlled.
  Codes hiện có: `VALIDATION_FAILED, MALFORMED_REQUEST, INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED, ACCESS_DENIED, RESOURCE_NOT_FOUND, RESOURCE_ALREADY_EXISTS, RESOURCE_IN_USE, CONCURRENT_MODIFICATION, CART_EMPTY, INVALID_CATEGORY_DEPTH, INVALID_MEDIA_RESOURCE_TYPE, INVALID_IMAGE_WIDTH, MEDIA_UPLOAD_FAILED, INTERNAL_ERROR`.
- **`ApplicationException.java`** — `RuntimeException` mang `ErrorCode code` + `Map<String,Object> parameters` (immutable, default rỗng). Có constructor có/không `cause`. KHÔNG chứa HttpStatus. `getMessage()` = `code.name()`.
- **`FieldViolation.java`** — `record(String field, String code, String message)`, `field` `@JsonInclude(NON_NULL)` cho object-level error.
- **`ApiProblemFactory.java`** — factory tĩnh:
  - `create(ErrorCode)` / `create(ErrorCode, Map)` → dựng `ProblemDetail` (title/detail/status + property `code`, `parameters`).
  - `statusFor(ErrorCode)` → switch ErrorCode → HttpStatus:
    - BAD_REQUEST: VALIDATION_FAILED, MALFORMED_REQUEST, CART_EMPTY, INVALID_CATEGORY_DEPTH, INVALID_IMAGE_WIDTH
    - UNAUTHORIZED: INVALID_CREDENTIALS, AUTHENTICATION_REQUIRED
    - FORBIDDEN: ACCESS_DENIED
    - NOT_FOUND: RESOURCE_NOT_FOUND
    - CONFLICT: RESOURCE_ALREADY_EXISTS, RESOURCE_IN_USE, CONCURRENT_MODIFICATION
    - UNPROCESSABLE_ENTITY(422): INVALID_MEDIA_RESOURCE_TYPE
    - BAD_GATEWAY(502): MEDIA_UPLOAD_FAILED
    - INTERNAL_SERVER_ERROR: INTERNAL_ERROR

### File ĐÃ XÓA

- `src/main/java/com/xdpsx/ecommerce/common/error/CustomExceptionHandler.java` (đã xóa khỏi disk, git báo `D`).

### File ĐÃ VIẾT LẠI HOÀN CHỈNH (coi như xong)

- **`common/error/GlobalExceptionHandler.java`** — extends `ResponseEntityExceptionHandler`:
  - `@ExceptionHandler(ApplicationException.class)` → dùng `ApiProblemFactory.create(code, params)`, log.debug.
  - `@ExceptionHandler(AccessDeniedException.class)` → ACCESS_DENIED, log.debug.
  - `@ExceptionHandler(AuthenticationException.class)` → INVALID_CREDENTIALS, log.debug.
  - `@ExceptionHandler(Exception.class)` → INTERNAL_ERROR, log.error có stacktrace.
  - override `handleMethodArgumentNotValid` → build `List<FieldViolation>` (field errors + global errors), property `errors`.
  - override `handleExceptionInternal` → gắn `code` property, giữ status của framework.
  - private `applyInstance(problem, request)` → set `instance` = `request.getRequestURI()` qua `ServletWebRequest`.
  - **Cần review kỹ**: logic `handleExceptionInternal` (điều kiện NOT_FOUND vs MALFORMED_REQUEST) chưa được test — xem rủi ro mục 4.
- **`auth/infrastructure/security/CustomAuthEntryPoint.java`** — dùng `ApiProblemFactory.create(AUTHENTICATION_REQUIRED)`, content-type `application/problem+json`, set instance = requestURI. KHÔNG còn lộ `authException.getMessage()`.

### Call site ĐÃ migrate xong (dùng ApplicationException/ErrorCode)

- `auth/application/AuthServiceImpl.java` — duplicate email → `RESOURCE_ALREADY_EXISTS` (params resourceType=user, field=email). ✅
- `cart/application/CartServiceImpl.java` — cartItem/product/user not found → `RESOURCE_NOT_FOUND`. ✅
- `user/application/UserServiceImpl.java` — user not found → `RESOURCE_NOT_FOUND`. ✅
- `catalog/brand/application/AbstractImageUpdatableService.java` — media not found → `RESOURCE_NOT_FOUND`. ✅
- `catalog/brand/application/BrandServiceImpl.java` — toàn bộ not found / duplicate / concurrent modification đã migrate. ✅
  - Lưu ý: dòng 138 còn 1 comment `// throw new BadRequestException(...)` cũ — chỉ là comment, không sao (có thể dọn).

---

## 2. Việc còn lại (TODO) — CHƯA làm

### 2.1. CategoryServiceImpl.java — **ĐANG MIGRATE DỞ, SẼ KHÔNG COMPILE**

Import đã đổi (bỏ `common.error.*`, thêm `ApplicationException`, `ErrorCode`, `java.util.Map`) nhưng **body còn dùng exception cũ** → hiện unresolved. Các dòng còn lại (số dòng theo lần check cuối, có thể lệch):

- `getCategory`: ĐÃ migrate. ✅
- `createCategory`:
  - `existsByName` → còn `DuplicateException(EMessage.DATA_EXISTS, request.name())` → đổi `RESOURCE_ALREADY_EXISTS` (params resourceType=category, field=name, value).
  - parent not found → còn `NotFoundException(EMessage.NOT_FOUND, request.parentId())` → `RESOURCE_NOT_FOUND` (category).
  - image not found → còn `NotFoundException(...request.imageId())` → `RESOURCE_NOT_FOUND` (media).
  - resource type sai → còn `InvalidResourceTypeException(EMessage.INVALID_RESOURCE_TYPE)` → `INVALID_MEDIA_RESOURCE_TYPE` (params expectedResourceType=category.resource()).
- `updateCategory`:
  - not found `NotFoundException` → `RESOURCE_NOT_FOUND` (category, id).
  - `ModifyExclusiveException` → `CONCURRENT_MODIFICATION` (category, id).
  - `DuplicateException` → `RESOURCE_ALREADY_EXISTS`.
- `checkCategoryDepth`: `BadRequestException(EMessage.INVALID_DEPTH, Category.MAX_DEPTH)` → `INVALID_CATEGORY_DEPTH` (params maxDepth).
- `updateCategoryImage`: `NotFoundException(newImageId)` → `RESOURCE_NOT_FOUND` (media); `InvalidResourceTypeException` → `INVALID_MEDIA_RESOURCE_TYPE`.
- `getParentCategory`: `NotFoundException(parentId)` → `RESOURCE_NOT_FOUND` (category).
- `deleteCategory`: `NotFoundException(id)` → `RESOURCE_NOT_FOUND`; `ModifyExclusiveException` → `CONCURRENT_MODIFICATION`; `InUseException(EMessage.IN_USE)` → `RESOURCE_IN_USE` (category, id).

> Tham chiếu pattern đã dùng ở `BrandServiceImpl` cho nhất quán (params `resourceType`, `resourceId`, `field`, `value`).

### 2.2. Các file CHƯA đụng tới

- **`catalog/product/application/ProductServiceImpl.java`** — nhiều `NotFoundException("Product/Category/Brand ... not found")`, `DuplicateException(slug)`, `BadRequestException("more than N images")`.
  - product/category/brand not found → `RESOURCE_NOT_FOUND` + params.
  - slug duplicate → `RESOURCE_ALREADY_EXISTS`.
  - vượt số ảnh → cần code mới, VD `INVALID_PRODUCT_IMAGE_COUNT` (hoặc dùng `MALFORMED_REQUEST`) — **quyết định**: thêm code riêng (đề xuất thêm `INVALID_PRODUCT_IMAGE_COUNT` → BAD_REQUEST).
- **`order/application/OrderServiceImpl.java`**:
  - `"Cart item is empty"` → `CART_EMPTY` (đã có code).
  - order not found (nhiều chỗ) → `RESOURCE_NOT_FOUND` (order).
  - `"You are not authorized to pay this order"` → cân nhắc `ACCESS_DENIED` (403) thay vì BadRequest 400. **Quyết định cần chốt** — plan nói giữ behavior, nhưng đây là authorization → đề xuất `ACCESS_DENIED`.
  - order by trackingNumber not found, user not found → `RESOURCE_NOT_FOUND`.
- **`media/api/MediaController.java`**: `BadRequestException(EMessage.INVALID_RESOURCE_TYPE, resource)` → `INVALID_MEDIA_RESOURCE_TYPE` (params resource). Lưu ý: hiện trả 400, code mới trả **422** — đây là thay đổi status có chủ đích theo plan (thống nhất invalid resource type).
- **`media/application/MediaServiceImpl.java`**:
  - `deleteMedia` not found → `RESOURCE_NOT_FOUND` (media).
  - `validateImageSize`: `BadRequestException(EMessage.INVALID_IMAGE_WIDTH, minWidth)` → `INVALID_IMAGE_WIDTH` (params minWidth).
  - `createMedia` catch: hiện `catch(Exception)` rộng → `RuntimeException(UPLOAD_IMAGE_FAILED)`. **Theo plan phải thu hẹp boundary**: chỉ lỗi upload của provider mới thành `MEDIA_UPLOAD_FAILED` (502, giữ cause, KHÔNG lộ message provider); lỗi DB/lập trình khác phải thành `INTERNAL_ERROR`. Cleanup phải giữ original cause.
    - Gợi ý: chỉ catch quanh `cloudinaryUploader.uploadFile(...)` → `MEDIA_UPLOAD_FAILED`; phần save DB để exception tự nhiên (→ INTERNAL_ERROR qua handler). Xem test `createMedia_ShouldDeleteUploadedFile_WhenSavingMediaFails` (mục 3) — test này sẽ phải đổi kỳ vọng.
- **`payment/infrastructure/vnpay/VNPayIpnHandler.java`**: `BadRequestException("Ipn is not valid")` → cần code, VD `MALFORMED_REQUEST` hoặc thêm `INVALID_PAYMENT_NOTIFICATION`. **Quyết định cần chốt** (đề xuất `MALFORMED_REQUEST` để không phình taxonomy).
- **Swagger docs** tham chiếu `ErrorDTO`/`ErrorDetailsDTO` (sẽ bị xóa):
  - `catalog/category/api/CategoryControllerApi.java`
  - `media/api/MediaControllerApi.java`
  - → thay `@Schema(implementation = ErrorDTO.class)` / `ErrorDetailsDTO.class` bằng schema mới. **Quyết định cần chốt**: tạo 1 DTO đại diện cho problem (VD `ApiProblem` + `ValidationProblem`) để doc OpenAPI, hay bỏ `@Content` error schema. Đề xuất tạo record mô tả đơn giản trong `common/error` chỉ dùng cho `@Schema`. `OpenApiDocumentationTest` có thể bị ảnh hưởng (đang assert schema name cụ thể — kiểm tra lại).

### 2.3. XÓA hệ thống cũ (chỉ sau khi migrate xong hết 2.1 + 2.2)

Xóa các file trong `common/error/`:
`APIException.java, APIMessage.java, BadRequestException.java, DuplicateException.java, EMessage.java, ErrorDTO.java, ErrorDetailsDTO.java, InUseException.java, InvalidResourceTypeException.java, ModifyExclusiveException.java, NotFoundException.java`.

> **Chú ý `APIMessage`**: `APIResponse.java` và `SMessage.java` dùng `APIMessage`. Plan nói GIỮ `SMessage` + `APIResponse` (success contract, ngoài scope). Vậy **KHÔNG xóa `APIMessage.java`** trừ khi refactor `SMessage`/`APIResponse` không còn dùng — nhưng chúng vẫn dùng. **=> GIỮ `APIMessage.java` và `SMessage.java`.** Chỉ xóa `EMessage.java` (error enum). Sửa lại danh sách xóa: bỏ `APIMessage.java` ra.

### 2.4. Tests cần cập nhật

- `catalog/brand/application/BrandServiceImplTest.java` — đang assert `NotFoundException`/`DuplicateException`/`ModifyExclusiveException` + `EMessage.*`. Đổi sang `ApplicationException` + assert `getCode()` (VD `ErrorCode.RESOURCE_NOT_FOUND`).
- `media/api/MediaControllerTest.java` — assert `$.message` = `EMessage.INVALID_RESOURCE_TYPE/BAD_VALIDATION`, `$.args[0]`, `$.status`. Đổi sang problem contract: `$.code`, `$.status`, `$.title`, validation `$.errors[*]`.
- `media/application/MediaServiceImplTest.java` — assert `BadRequestException`(INVALID_IMAGE_WIDTH), `RuntimeException`(UPLOAD_IMAGE_FAILED), `NotFoundException`. Đổi sang `ApplicationException` + code tương ứng; riêng case DB-save-fail (1.7) phải phản ánh boundary mới (INTERNAL_ERROR chứ không phải MEDIA_UPLOAD_FAILED).
- **Tests MỚI cần thêm** (theo plan Test plan, đại diện):
  1. missing resource → 404 + `RESOURCE_NOT_FOUND` + params an toàn.
  2. validation → 400 + `errors[]` giữ nhiều violation.
  3. malformed JSON → 400 controlled (không rơi 500).
  4. unexpected exception → 500 `INTERNAL_ERROR`, không lộ message gốc.
  5. `CustomAuthEntryPoint` → 401 `AUTHENTICATION_REQUIRED`, không lộ framework message.
  6. media upload fail → `MEDIA_UPLOAD_FAILED` (502), không lộ message provider; lỗi không liên quan → `INTERNAL_ERROR`.

---

## 3. Quyết định cần chốt trước khi làm tiếp (open decisions)

1. **Product vượt số ảnh** → thêm `ErrorCode.INVALID_PRODUCT_IMAGE_COUNT` (BAD_REQUEST) hay tái dùng `MALFORMED_REQUEST`? (đề xuất: thêm code riêng)
2. **VNPay IPN invalid** → `MALFORMED_REQUEST` hay thêm `INVALID_PAYMENT_NOTIFICATION`? (đề xuất: `MALFORMED_REQUEST`)
3. **Order "not authorized to pay"** → `ACCESS_DENIED` (403, đúng bản chất) hay giữ 400? (đề xuất: `ACCESS_DENIED`; đây là thay đổi status có chủ đích)
4. **Media invalid resource type** hiện 400 → code mới 422. Xác nhận OK đổi status (plan đã nêu thống nhất về 422).
5. **Swagger error schema** → tạo DTO đại diện problem cho `@Schema`, hay bỏ error `@Content`? (đề xuất: tạo record `ApiProblemSchema` đơn giản trong `common/error`, chỉ dùng cho doc)
6. **`instance`** — hiện set thủ công từ `getRequestURI()`. Kiểm tra `ResponseEntityExceptionHandler` của Spring 7 có tự set instance không để tránh trùng/thừa (chưa verify runtime).

---

## 4. Rủi ro / lưu ý kỹ thuật

- **`GlobalExceptionHandler.handleExceptionInternal`** logic gán `code` (NOT_FOUND vs MALFORMED_REQUEST) chưa chạy thử. Spring 7 `ResponseEntityExceptionHandler` có thể đã tự tạo `ProblemDetail` (body instanceof ProblemDetail) — cần test thực tế để chắc chắn status framework không bị ghi đè sai.
- **ProblemDetail serialization với Jackson 3 (`tools.jackson`)**: project dùng `tools.jackson.databind.ObjectMapper` (Jackson 3, Spring Boot 4). Cần đảm bảo `ProblemDetail` serialize ra JSON đúng (các property mở rộng `code`, `parameters`, `errors`). Spring Boot 4 có `JsonProblemDetailsConfiguration` (đã thấy trong jar `spring-boot-jackson-4.1.1`) tự đăng ký mixin cho `ProblemDetail` — nhiều khả năng OK, nhưng **chưa verify bằng test**.
- **Content-Type**: response sẽ là `application/problem+json`. Test nào assert `application/json` cần cập nhật.
- **`Map.of` không nhận giá trị null** — các param như `resourceId` phải non-null tại mọi throw site. Kiểm tra khi migrate (VD productId trong cart có thể null?).
- **Không chạy verify runtime nặng** trong lúc implement (theo偏好 user) — nhưng CR này đổi contract nên **bắt buộc** phải chạy `.\mvnw.cmd test` ở cuối.

---

## 5. Lệnh verify (chạy khi resume)

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
.\mvnw.cmd -Dtest=BrandServiceImplTest test        # focused, sau khi sửa test
.\mvnw.cmd test                                     # full suite
git diff --check
git diff --stat
```

---

## 6. Thứ tự resume đề xuất

1. Hoàn tất **2.1 CategoryServiceImpl** (đang hỏng compile) → build thử.
2. Migrate **2.2** từng file: Product → Order → Media(Controller+Service) → VNPay → Swagger docs.
3. Chốt các **open decisions** mục 3 (hỏi user nếu ảnh hưởng behavior).
4. **Xóa** hệ exception cũ (mục 2.3, GIỮ `APIMessage.java` + `SMessage.java`).
5. Cập nhật + thêm **tests** (mục 2.4).
6. Chạy `spotless:apply` → `spotless:check` → focused test → `mvnw test`.
7. `git diff --stat` rà scope, đảm bảo KHÔNG đụng success contract (`APIResponse`, `SMessage`).

---

## Phụ lục: git status lúc dừng

```
A  docs/cr-standardize-api-error-handling-plan.md
 M src/main/java/com/xdpsx/ecommerce/auth/application/AuthServiceImpl.java
 M src/main/java/com/xdpsx/ecommerce/auth/infrastructure/security/CustomAuthEntryPoint.java
 M src/main/java/com/xdpsx/ecommerce/cart/application/CartServiceImpl.java
 M src/main/java/com/xdpsx/ecommerce/catalog/brand/application/AbstractImageUpdatableService.java
 M src/main/java/com/xdpsx/ecommerce/catalog/brand/application/BrandServiceImpl.java
 M src/main/java/com/xdpsx/ecommerce/catalog/category/application/CategoryServiceImpl.java   <-- ĐANG HỎNG COMPILE
 D src/main/java/com/xdpsx/ecommerce/common/error/CustomExceptionHandler.java
 M src/main/java/com/xdpsx/ecommerce/common/error/GlobalExceptionHandler.java
 M src/main/java/com/xdpsx/ecommerce/user/application/UserServiceImpl.java
?? src/main/java/com/xdpsx/ecommerce/common/error/ApiProblemFactory.java
?? src/main/java/com/xdpsx/ecommerce/common/error/ApplicationException.java
?? src/main/java/com/xdpsx/ecommerce/common/error/ErrorCode.java
?? src/main/java/com/xdpsx/ecommerce/common/error/FieldViolation.java
```
