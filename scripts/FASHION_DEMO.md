# Dữ liệu thời trang mẫu

**Bộ mặc định hiện tại:** Product Service đã có seed 24 sản phẩm của Mộc Basics trong resource và tự nạp khi catalog hoàn toàn rỗng. Xem `../product-service/HOMEPAGE_SEED.md`. Không cần chạy script upload bên dưới để có dữ liệu trang chủ; nội dung còn lại ghi lại cách tạo/cập nhật catalog mẫu thủ công trước đây.

Ngày 22/10/2026 đã bổ sung 30 ảnh góc chụp của đúng 15 mẫu hiện có lên Cloudinary và gắn qua API `product-images`. Catalog local hiện có **24 sản phẩm, 69 ảnh**; 15 mẫu được bổ sung có 4 ảnh/mẫu. Ảnh đại diện, giá và tồn kho được giữ nguyên. Các ảnh bổ sung lấy từ cùng ID sản phẩm DummyJSON đã dùng khi seed; không ghép ảnh của mẫu khác. Bản sao dữ liệu trước khi thêm, URL nguồn/Cloudinary và kết quả kiểm tra nằm trong `be/.local/fashion-images-20261022/`. Đã kiểm tra 30 URL CDN và thử chọn ảnh mới trên trang chi tiết của áo sơ mi, áo thun và đầm.

Chạy tại `be`:

```powershell
node scripts/seed-fashion.mjs
node scripts/seed-fashion.mjs --apply
node scripts/seed-fashion.mjs --apply --restock=100
node scripts/setup-fashion-shop.mjs
```

Lệnh đầu chỉ xem kế hoạch. Lệnh thứ hai thêm 4 gian hàng, 24 sản phẩm quần áo, 96 biến thể S/M/L/XL và 39 ảnh sản phẩm; upload ảnh và 4 logo lên Cloudinary đã cấu hình trong backend `.env`. Biến thể mới có 100 đơn vị mỗi size. `--restock=100` tăng các biến thể demo còn dưới 100 lên 100, không giảm tồn kho cao hơn và không sửa sản phẩm khác.

`setup-fashion-shop.mjs` tạo tài khoản local **moc.basics.demo** với quyền SHOP_OWNER, đồng bộ qua user-service và liên kết đúng shop Mộc Basics. Thông tin đăng nhập nằm trong `be/.local/fashion-seed-v1/shop-login.json`; mật khẩu được tạo ngẫu nhiên và không ghi vào source. Script dùng Keycloak local, tài khoản admin theo `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` hoặc cấu hình mặc định của Compose; không thay đổi tài khoản cá nhân khác. Chạy lại dùng tài khoản demo đã tạo, không đổi mật khẩu. Sau khi đăng nhập, vào `/merchant/products` để thêm sản phẩm, nhập tồn kho cho từng size và upload ảnh. Sản phẩm mới ở trạng thái chờ duyệt; admin duyệt trước khi xuất hiện công khai.

Script setup cũng mở rộng cột mô tả sản phẩm từ `varchar(255)` sang `text` cho database local đã tồn tại, giữ nguyên nội dung. Điều này tránh lỗi 500 khi lưu mô tả dài từ form đăng sản phẩm.

Script đọc `be/.env`, sau đó `be/product-service/.env`; biến môi trường của terminal được ưu tiên cuối cùng. Kết nối lấy từ `DB_URL` hoặc `PRODUCT_DB_PORT`/`PRODUCT_DB_NAME`, tài khoản từ `DB_USERNAME`/`DB_PASSWORD` hoặc `POSTGRES_USER`/`POSTGRES_PASSWORD`. Chỉ cho phép database local. Cần Node 18+ và công cụ PostgreSQL; đặt `POSTGRES_BIN` nếu thư mục cài khác `C:\Program Files\PostgreSQL\18\bin`.

Trước khi ghi, script sao lưu các bảng catalog vào `be/.local/fashion-seed-v1/before-*.sql`. ID/SKU ổn định, `ON CONFLICT DO NOTHING`: chạy lại không tạo bản ghi trùng, không đặt lại tồn kho đã thay đổi. Không xóa dữ liệu khác. Toàn bộ thao tác database nằm trong một transaction.

Cloudinary dùng thư mục `scanms/demo-fashion-v1`, public ID ổn định, `overwrite=false`; manifest URL và bản sao SQL nằm trong `.local/fashion-seed-v1`, không lưu API secret. Không commit `.local` hoặc `.env`. Nếu database rollback sau upload, ảnh đã upload vẫn được giữ để lần chạy sau dùng lại.

Tên shop, giá, bảng size và tồn kho là dữ liệu minh họa, được ghi rõ trong mô tả. Không tạo đơn hàng, đánh giá, điểm sao, hoa hồng hoặc chứng từ KYC giả. Nếu chỉ chạy seed, chủ shop dùng ID demo chưa đăng nhập được. Chạy thêm script setup để quản lý Mộc Basics; ba shop còn lại vẫn là shop trưng bày. Muốn vận hành shop thật, dùng luồng hồ sơ thông thường.

Ảnh sản phẩm từ các catalog demo công khai [DummyJSON](https://dummyjson.com/docs/products) và [Fake Store API](https://fakestoreapi.com/). Các ID nguồn được cố định trong script; từng URL nguồn/Cloudinary được lưu trong manifest để truy xuất. Logo là lettermark do script tạo. Đây là ảnh minh họa cho dữ liệu demo, không phải cam kết hàng hóa thật.

Sau khi seed, restart `product-service` để dùng API mới. Tải lại frontend bằng Ctrl+Shift+R để bỏ cache trình duyệt. Các API kiểm tra:

- `GET /api/v1/public/products?limit=100`
- `GET /api/v1/public/stores`
- `GET /api/v1/public/products/{productId}/landing`
- `GET /api/v1/public/products/{productId}/stock`

Script xóa đúng các key cache catalog Redis của product-service nếu Redis local truy cập được. Không flush Redis. Nếu không xóa được cache, các key cũ tự hết hạn theo TTL (tối đa 10 phút).
