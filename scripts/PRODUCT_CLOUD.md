# PostgreSQL online cho Product

Mục tiêu: Product Service của các thành viên đọc chung dữ liệu qua Internet. Dữ liệu khởi đầu là catalog hiện có; User, Keycloak, Order và Payment giữ cấu hình riêng.

## Đã chuyển lên online ngày 26/10/2026

- Đã restore và so sánh số dòng/hash của toàn bộ 9 bảng Product vào database `neondb` trên Neon PostgreSQL 18.
- Có 24 sản phẩm, 96 biến thể, 69 ảnh và một shop Mộc Basics; giữ nguyên ID và dữ liệu hiện có.
- Product Service trên máy chủ đã chuyển sang tài khoản `scanms_product_app`, SSL và Hibernate validate. Tài khoản ứng dụng chỉ đọc/ghi dữ liệu, không sở hữu schema hoặc có quyền superuser/tạo database.
- API Gateway đã trả đủ 24 sản phẩm; chi tiết và tồn kho hoạt động. Có xác nhận JDBC đang kết nối vào database online.
- Kết quả lưu trong `.local/product-cloud/migration-receipt.json`, `app-account-receipt.json`, `live-verification.json`. Không đưa các file mật khẩu vào Git.

## Máy thành viên dùng database online hiện có

Nhận gói `SCANMS-Product-PostgreSQL-Online.zip` từ người giữ cấu hình và giải nén vào **thư mục `be`**. Gói chỉ chứa kết nối tài khoản ứng dụng, không chứa tài khoản quản trị hoặc dump dữ liệu.

Chạy trong `be`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Configure-ProductCloud.ps1 -ConnectionFile .local/product-cloud/app-connection.env -Apply
```

Restart **product-service** trong IntelliJ, hoặc dùng `scripts/Start-LocalService.ps1 -Service product-service`. Nếu Run Configuration đã đặt DB_URL/DB_USERNAME/DB_PASSWORD/JPA_DDL_AUTO riêng, bỏ các giá trị cũ đang ghi đè file module. FE vẫn gọi backend local của từng người; backend Product sẽ đọc cùng PostgreSQL online. Máy thành viên chỉ cần có Internet, không cần cùng Wi-Fi/LAN với máy giữ dữ liệu.

Giữ file kết nối trong `.local`, không commit. Gói online này thay cho gói LAN cũ.

## Bước người sở hữu tài khoản thực hiện

1. Mở https://console.neon.tech, đăng nhập và tạo project `scanms-product` trên gói Free. Có thể giữ database mặc định `neondb`; chọn PostgreSQL 17 để khớp nguồn local.
2. Chọn **Connect**, lấy **direct connection string**, không dùng địa chỉ có `-pooler` cho lần restore ban đầu.
3. Điền vào `be/.local/product-cloud/connection.env`:

```env
DATABASE_URL=postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require
```

File thật đã được tạo với giá trị rỗng. Không đưa file này vào Git.

## Phần chuyển dữ liệu

- `node scripts/export-product-cloud.mjs` tạo dump Product tại `.local/product-cloud/`, kiểm tra archive có đủ bảng dữ liệu và ghi counts/hash vào `export-receipt.json`. Không sửa nguồn và không upload.
- `node scripts/migrate-product-cloud.mjs` kiểm tra database đích; thêm `--apply` chỉ để restore lần đầu vào database trống. Script từ chối đích có bảng/enum, không dùng `--clean`, truyền mật khẩu qua biến môi trường và đối chiếu toàn bộ dữ liệu sau restore. `--verify` kiểm tra lại theo biên bản đã lưu.
- `node scripts/provision-product-cloud-app.mjs` tạo tài khoản ứng dụng giới hạn sau khi migration thành công và ghi riêng `app-connection.env`; ứng dụng dùng file này, giữ connection.env của quản trị riêng cho migration.
- Đối chiếu sản phẩm, biến thể, ảnh, shop và các quan hệ trong database đích trước khi chuyển ứng dụng.
- Xem cấu hình sắp áp dụng bằng lệnh sau, chạy trong `be`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/Configure-ProductCloud.ps1 -ConnectionFile .local/product-cloud/connection.env
```

- Thêm `-Apply` sau khi restore đã được kiểm tra để cập nhật riêng `product-service/.env`. Script sao lưu file cũ, bật SSL và dùng Hibernate validate. Restart Product Service rồi kiểm tra API public. Các máy thành viên dùng cùng cấu hình DB online, giữ cấu hình OAuth của mình.

Chuỗi kết nối dùng chuẩn PostgreSQL, xem https://neon.com/docs/get-started/connect-neon. File export/connection nằm trong `.local` đã được ignore.
