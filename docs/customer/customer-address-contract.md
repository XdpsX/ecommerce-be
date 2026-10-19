# Profile và địa chỉ khách hàng

Profile `name` là tên hiển thị local, bắt buộc, tối đa 64 ký tự. Đổi name không thay email, role,
provider, password, avatar hoặc profile phía OAuth.

## Address book

| Field | Giới hạn |
| --- | --- |
| `recipientName` | Bắt buộc, 128 ký tự |
| `phoneNumber` | Bắt buộc, 8–15 chữ số ASCII, có thể bắt đầu bằng `+` |
| `addressLine` | Bắt buộc, 255 ký tự |
| `wardCommune`, `district`, `provinceCity` | Bắt buộc, mỗi field 128 ký tự |
| `postalCode` | Optional, 20 ký tự; blank thành null |

Text được trim trước validation/persistence. Response trả address ID và structured fields, không expose User entity.
Customer chỉ list/replace/delete địa chỉ của mình; ID không tồn tại và ID của người khác cùng trả RESOURCE_NOT_FOUND.
Có thể có nhiều địa chỉ, hard-delete được; chưa có default address, custom ordering hoặc uniqueness rule.

## Khi tích hợp Checkout

Address book có thể thay đổi, không phải lịch sử Order. Checkout nhận addressId phải kiểm tra ownership rồi copy
đầy đủ structured fields vào Order trong cùng transaction. Sửa/xóa address không được đổi Order đã đặt.

`orders.address` và `orders.mobile_number` hiện là legacy scalar fields; tài liệu này chưa khẳng định có đầy đủ
structured shipping snapshot và không yêu cầu FK từ Order tới address book.
