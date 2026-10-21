# Hợp tác Shop – KOL

Trang `/merchant/collaborations` lấy dữ liệu qua `GET /api/v1/referrals/shop-collaborations`.
API xác định gian hàng qua Product `/stores/mine` với JWT của người đang đăng nhập;
không nhận quyền sở hữu từ `storeId` phía trình duyệt. Chỉ SHOP_OWNER/SHOP_MANAGER được xem.
ADMIN/MANAGER dùng `GET /api/v1/referrals/admin-collaborations` để xem toàn sàn.

Danh sách gồm referral theo sản phẩm của shop và mỗi KOL được mời trong một phiên livestream.
Một KOL có thể có nhiều nội dung hợp tác; đây không phải danh sách hợp đồng độc quyền.
Không có dữ liệu thì API trả danh sách rỗng. Dịch vụ Product/User lỗi thì API trả lỗi,
không giả thành danh sách rỗng. Không tạo dữ liệu mẫu vào cơ sở dữ liệu đang chạy.

Giao diện hỗ trợ tìm kiếm không dấu, lọc hình thức/trạng thái, làm mới bỏ cache,
xem chi tiết, sao chép **mã** referral, xem sản phẩm và mở trang quản lý livestream.
Các thao tác này không tự ký hợp đồng, thay đổi hoa hồng hay gửi lời mời.
Để có dữ liệu: duyệt hồ sơ KOL qua chức năng quản trị hiện có, sau đó KOL tạo referral
hoặc shop tạo phiên/mời KOL ở trang Livestream. Lời mời hiển thị cả khi chưa có referral.
Khi nguồn dữ liệu chưa cung cấp tên KOL, giao diện hiển thị mã KOL thay vì tên giả.

Kiểm thử backend: `mvn -pl promotion-affiliate-service test`.
Kiểm thử trình duyệt bằng API giả: đặt `GHN_UI_TEST_URL=http://localhost:5173`,
chạy `node --test tests/collaborations.browser.test.mjs` trong SCANMS_FE.
