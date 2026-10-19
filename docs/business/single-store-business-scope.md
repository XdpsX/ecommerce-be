# Phạm vi nghiệp vụ: một cửa hàng

M1 phục vụ một cửa hàng, một catalog và một nhóm vận hành. Đây là phạm vi nghiệp vụ mục tiêu;
không phải xác nhận mọi flow bên dưới đã implement xong. Tiến độ nằm trong working notes local.

## Actor

- `USER`: mua hàng, quản lý profile, địa chỉ, cart và order của mình.
- `ADMIN`: vận hành catalog, SKU, tồn kho, giá, order, payment và refund.
- Storefront là giao diện công khai; Backoffice yêu cầu ADMIN. Không có SELLER/store membership trong M1.

## Catalog

| Thành phần | Quy tắc |
| --- | --- |
| Category | Tối đa 3 cấp; không cycle; có move/reorder; visible khi bản thân và mọi ancestor active |
| Brand | Tên unique không phân biệt hoa/thường; category association optional; storefront chỉ thấy brand active |
| Product | Một Category, một Brand, gallery có thứ tự; tạo mới draft; publish/unpublish riêng |
| Visibility | Product published, Brand active, Category effectively active và có Variant active |
| Variant/SKU | Đơn vị bán; SKU uppercase unique, combination unique trong Product; SKU/combination bất biến |
| Option/value | Tối đa một value/option trong Variant; các Variant active cùng Product dùng cùng tập option |
| Slug | Product/Category unique, normalize server-side; rename không tự đổi URL; DB constraint là bảo vệ cuối |

Chỉ tạo Variant thực sự bán. Option/value dành cho thuộc tính tạo SKU; chưa xây generic attribute/EAV engine.
Không xóa Category có child/reference, Brand còn Product, hoặc Product/Variant làm mất lịch sử downstream.

Media lifecycle, ownership và upload được mô tả tại [Sử dụng Media](../media/usage.md).

## Luồng mua hàng

| Module | Nguyên tắc |
| --- | --- |
| Inventory | Theo SKU; available = onHand - reserved; không âm, không oversell; adjustment có reason/audit |
| Pricing | Theo SKU; một currency cấu hình; decimal money; OrderItem snapshot giá/discount/currency |
| Cart | Theo customer và Variant; quantity dương, cộng số lượng khi thêm trùng; không reserve tồn; giá chỉ estimate |
| Checkout | Validate ownership/cart/address, lock dữ liệu cần thiết, tính giá server-side, reserve tồn, tạo snapshot |
| Order | Retry checkout không tạo duplicate; snapshot sản phẩm/SKU/option/giá/địa chỉ độc lập dữ liệu hiện tại |
| Payment | Có attempt/reference; verify callback và đối chiếu amount/currency; duplicate/out-of-order idempotent |
| Cancellation/refund | Transition xác định ai được thao tác, khi nào release/restock và khi nào refund |
| Fulfillment | Vận hành thủ công trong Backoffice; payment state và fulfillment state phải phân biệt |

Catalog mutation và attachment cùng transaction. Provider calls chạy ngoài transaction DB dài.
Concurrent cart/checkout/payment không được làm mất update, tạo side effect lặp hoặc bán vượt tồn.

## Tài khoản và địa chỉ

Registration tạo USER; không có public flow tạo ADMIN. Email canonical/unique; không tự suy account linking
khi LOCAL và OAuth trùng email. Customer chỉ truy cập dữ liệu của mình.

Chi tiết nằm trong [phiên đăng nhập](../auth/local-auth-session-contract.md) và
[địa chỉ giao hàng](../customer/customer-address-contract.md).

## Chưa làm trong M1

- Marketplace, multi-vendor, seller onboarding, tenant/store permissions, commission và payout.
- Multi-warehouse, generic promotion/EAV engine, tích hợp hãng vận chuyển thật.
- Partial refund nếu chưa có yêu cầu riêng; hạ tầng phân tán khi chưa có nhu cầu cụ thể.

M1 hoàn tất khi luồng catalog → SKU/tồn/giá → cart → checkout → payment → fulfillment/cancel/refund nhất quán,
được bảo vệ bằng authorization, snapshots, idempotency, concurrency guards và tests phù hợp.
