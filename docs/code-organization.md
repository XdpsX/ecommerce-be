# Tổ chức code

## Theo module, rồi theo trách nhiệm

Đặt code cạnh nghiệp vụ của nó: `auth`, `media`, `catalog/category`, `catalog/product`...
Trong mỗi module, dùng các package khi cần:

| Package | Nội dung |
| --- | --- |
| `api` | Controller, API documentation interface, HTTP request/response DTO, request validation |
| `application` | Use case, orchestration, transaction boundary, command/result và contract cần cho use case |
| `domain` | Entity, value object, enum và business invariants |
| `persistence` | Repository, query và database access |
| `infrastructure` | Provider adapter, SDK integration, scheduler và tích hợp bên ngoài |

Không bắt buộc tạo đủ package nếu module chưa cần. `common` chỉ chứa phần thực sự dùng chung, không gom nghiệp vụ vào đó.

## Bên trong application

Giữ phẳng khi còn dễ đọc. Khi nhiều flow khiến package khó theo dõi, nhóm các class thường được đọc và thay đổi
cùng nhau theo nghiệp vụ. Service, command/result và transaction helper liên quan có thể nằm cạnh nhau.

Ví dụ định hướng, không phải cấu trúc bắt buộc hoặc mô tả code đã được chuyển:

```text
media/application/          catalog/category/application/
├── upload/                ├── CategoryService.java
├── attachment/            ├── CategoryServiceImpl.java
├── processing/            └── ...
├── cleanup/
├── delivery/
└── storage/
```

Media có nhiều flow nên có thể chia như trên. Category có thể giữ phẳng; chỉ tách `hierarchy`, `import`...
khi thực sự cần. Các module không phải có cùng cây thư mục.

## Giữ ranh giới đơn giản

- API xử lý HTTP và map DTO; application điều phối use case; domain bảo vệ invariant.
- Module khác gọi application contract, tránh tự đổi lifecycle hoặc truy cập repository nội bộ của module đó.
- Contract với provider đặt gần use case sử dụng; SDK implementation ở infrastructure.
- Không tạo interface cho mọi service hoặc chia sẵn `service/impl/dto/exception` trong từng nhóm nhỏ.
- Class dùng chung nhiều flow đặt ở nhóm có trách nhiệm phù hợp; không duplicate hoặc tách class chỉ để vừa cây thư mục.
- Test đặt theo package của code được kiểm tra khi phù hợp; test xuyên nhiều flow không phải chia theo từng service.

Khi tổ chức lại package, giữ nguyên API, behavior và transaction boundaries. Dependency cần sửa phải được xem xét
riêng; di chuyển file không tự giải quyết dependency sai. Cập nhật imports, test references và cấu hình scan nếu có.
