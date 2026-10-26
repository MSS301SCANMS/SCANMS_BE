# Catalog mặc định cho trang chủ

Product Service có sẵn dữ liệu nền trong source, không cần upload lại ảnh hoặc chép database từ máy khác:

- 1 shop **Mộc Basics**.
- 6 danh mục và **24 sản phẩm**.
- **96 biến thể S/M/L/XL**, giữ nguyên ID, SKU, giá và tồn kho trong bản catalog đóng gói ngày 26/10/2026.
- **69 ảnh** với URL Cloudinary công khai hiện có. Không cần Cloudinary API key để đọc ảnh seed.

## Cách hoạt động

Khi Product Service khởi động, `HomepageCatalogInitializer` gọi `HomepageCatalogSeedService` qua implementation. Chỉ khi cả năm bảng `stores`, `product_categories`, `products`, `product_variants`, `product_images` đều rỗng thì mới nạp `src/main/resources/seed/homepage-catalog.sql`.

SQL chạy trong một transaction, dùng khóa PostgreSQL để tránh hai instance cùng seed. Nếu nạp lỗi, toàn bộ thay đổi rollback. Sau khi thành công, xóa đúng các cache catalog của ứng dụng để trang chủ đọc được dữ liệu mới.

Database đã có bất kỳ dữ liệu catalog nào sẽ được **bỏ qua toàn bộ**. Khởi động lại không đặt lại giá, tồn kho hoặc dữ liệu đã sửa. PostgreSQL online hiện có 24 sản phẩm cũng được giữ nguyên.

Trang chủ vẫn lấy dữ liệu qua API như trước. Các ID seed có bản ghi thật trong database, dùng tiếp được cho chi tiết, size, tồn kho và checkout; không dùng dữ liệu giả thay API khi backend lỗi.

## Máy mới chạy dự án

1. Khởi động PostgreSQL Product DB và các service cần thiết.
2. Dùng cấu hình local mặc định `JPA_DDL_AUTO=update` để Hibernate tạo schema trên database mới.
3. Chạy Product Service từ source mới. Catalog sẽ tự nạp nếu rỗng.
4. Kiểm tra `http://localhost:8080/api/v1/public/products?limit=100` qua Gateway, hoặc cổng Product `8082`.

Không cần chạy `seed-fashion.mjs --apply` cho bộ mặc định này. Gói SQL đã mang sẵn ảnh và metadata, không gọi DummyJSON/Fake Store API khi khởi động.

Tắt tự seed trong `product-service/.env` hoặc biến môi trường:

```env
CATALOG_SEED_ENABLED=false
```

Với môi trường kiểm thử không có Redis hoặc cần đọc thẳng DB, có thể đặt `CATALOG_CACHE_ENABLED=false`; mặc định cache Redis vẫn bật.

## Dữ liệu và quyền shop

Seed chỉ chứa catalog, không có mật khẩu, API secret, thông tin liên hệ/KYC/ngân hàng của chủ shop, đánh giá, livestream, đơn hàng hay số dư ví. Rating khởi đầu bằng 0 và không tạo đánh giá giả.

Shop giữ nguyên `storeId` và `ownerUserId` của Mộc Basics đang dùng trong dự án. Seed không tạo User/Keycloak hoặc tự cấp vai trò. Muốn quản lý shop trên một User DB khác, cần liên kết đúng tài khoản chủ shop bằng quy trình quản trị của môi trường đó; việc xem catalog công khai không cần tài khoản này.

`scripts/export-homepage-catalog-seed.mjs` dùng để cập nhật lại resource từ catalog hiện tại theo whitelist trường. File kết nối riêng được đọc từ `.local`, không được ghi vào seed.

## Xác minh ngày 26/10/2026

- Đã chạy Product Service trên một PostgreSQL QA mới: tự nạp đủ 1 shop, 6 danh mục, 24 sản phẩm, 96 biến thể và 69 ảnh. API public listing, chi tiết, ảnh và size hoạt động; không có bản ghi mồ côi.
- Đổi giá và đặt tồn kho một biến thể về 0 rồi restart: các chỉnh sửa giữ nguyên, không phát sinh bản ghi trùng.
- Cố ý làm một INSERT vi phạm constraint trong QA: tất cả hàng seed đều rollback, kể cả shop/danh mục đã nạp trước đó. Database QA đã được dọn sau kiểm tra.
- Build/package Product thành công. Bộ test catalog/inventory/shop liên quan: 12 test, 11 đạt và 1 bỏ qua theo cấu hình test PostgreSQL tùy chọn.
- Bộ test Product đầy đủ hiện còn 3 failure trong `LivestreamPartnershipFlowTest` và 1 error trong `LivestreamReferenceUpdateTest`, ngoài phần seed; chưa đánh dấu cả bộ test là đạt.
- Product đang chạy với database online đã có dữ liệu: initializer bỏ qua seed. Đã kiểm tra lại dữ liệu online và API còn đủ 24 sản phẩm.
