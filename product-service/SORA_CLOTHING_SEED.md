# Sora Skin — sản phẩm quần áo mẫu

Product Service tự nạp `src/main/resources/seed/sora-clothing.sql` khi khởi động.
Khác với catalog mặc định chỉ chạy trên database trống, seed này chạy cả khi đã có
gian hàng Sora Skin. Mỗi lần chạy chỉ bổ sung dòng còn thiếu, không đặt lại giá,
tồn kho, chủ sở hữu, thông tin hoặc trạng thái duyệt đã được chỉnh sửa.

- **Áo sơ mi caro xanh dài tay Sora**, 299.000đ.
- Bốn biến thể S/M/L/XL, ban đầu 25 chiếc/size; SKU `SORA-CARO-XANH-*`.
- ID sản phẩm: `a77f64b0-f46d-4c9b-87db-c07f797a2b21`.
- **Áo thun cotton trắng cổ tròn Sora**, 199.000đ; ID `c32f57a8-66c9-4f93-8a3c-97406e58b201`.
  Bốn size S/M/L/XL, ban đầu 20 chiếc/size; SKU `SORA-THUN-TRANG-*`.
  Ảnh có sẵn trong FE tại `/assets/products/sora-white-tshirt.png`.
- Sử dụng gian hàng có tên Sora Skin hiện có, kể cả ID khác máy gốc.
- Nếu chưa có gian hàng, tạo gian hàng mẫu Sora Skin. Không tạo tài khoản người dùng.
- Trên máy dùng tài khoản chủ shop khác, cấu hình `SORA_SEED_OWNER_USER_ID` là
  `userId` thực của chủ shop **trước lần khởi động đầu** để chủ shop nhận tin nhắn và đơn.
  Biến này không chuyển quyền sở hữu gian hàng đã tồn tại.
- Tắt seed riêng này bằng `SORA_CATALOG_SEED_ENABLED=false`, hoặc tắt toàn bộ
  catalog mẫu bằng `CATALOG_SEED_ENABLED=false` (ví dụ môi trường production).

## Ảnh và giao diện

Ảnh được lưu trong **SCANMS_FE/public/assets/products/sora-caro-shirt.webp**;
database dùng đường dẫn `/assets/products/sora-caro-shirt.webp`, không phụ thuộc
Cloudinary khi chạy. Lấy cả thay đổi FE lẫn BE, khởi động lại Product Service,
Gateway và FE để có dữ liệu, ảnh và nút nhắn tin trên máy mới.
Ảnh lấy từ catalog thời trang đã có trong repo (`demo-fashion-v1/products/d-83-1.webp`,
nguồn gốc DummyJSON); là ảnh sản phẩm minh họa, không phải ảnh hàng hóa thực của shop.

## Luồng mua và nhắn tin

Marketplace đọc `/api/v1/public/products`, không có sản phẩm giả trong giao diện.
Tên shop mở trang shop; ảnh/tên/nút **Mua ngay** mở trang sản phẩm hiện có để chọn
size và mua. Khách đăng nhập bấm **Nhắn tin với shop** trên thẻ hoặc trang chi tiết.
Cuộc trò chuyện và tin nhắn văn bản được lưu vào `store_chat_conversations` và
`store_chat_messages`. Chủ shop xem và phản hồi tại `/merchant/customer-messages`.
Spring stack dùng HTTP và cập nhật mỗi 5 giây; không cần Socket.IO.
Chỉ khách và chủ sở hữu hiện tại của gian hàng được đọc/gửi/đánh dấu đã đọc.
Mã tin nhắn hỗ trợ gửi lại không tạo bản sao. Không tạo lời chào giả của shop.

API nằm trên Product Service, Gateway chuyển `/api/v1/chat/**` về dịch vụ này.
Tính năng này hỗ trợ chat văn bản khách–shop; không bổ sung chat KOL, đính kèm tệp,
xóa lịch sử hoặc thỏa thuận chiến dịch qua chat.

## Kiểm tra

`SoraCatalogAndChatIntegrationTest` chạy trên PostgreSQL QA riêng, kiểm tra seed
lặp lại, giữ tồn kho/giá/chủ shop, tái sử dụng shop, catalog công khai, chat hai chiều,
quyền truy cập, đã đọc, chống trùng tin nhắn và tạo hội thoại đồng thời.
Chỉ đặt `SORA_TEST_POSTGRES_URL` trỏ tới database tạm có tên `scanms_qa_sora`;
test dùng `create-drop`, tuyệt đối không trỏ tới database làm việc.
