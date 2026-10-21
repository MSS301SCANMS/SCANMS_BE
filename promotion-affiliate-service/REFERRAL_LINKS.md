# Liên kết tiếp thị sản phẩm (Spring)

Frontend tạo liên kết qua `POST /api/v1/referrals`. Backend xác định hồ sơ từ
người dùng đang đăng nhập; hồ sơ phải APPROVED. Sản phẩm phải ACTIVE và gian
hàng phải APPROVED. Danh mục chọn sản phẩm dùng
`GET /api/v1/public/products?eligibleForReferral=true` (product-service).

Form lưu tên liên kết, kênh chia sẻ và hạn dùng tùy chọn. `expiresAt` là UTC
LocalDateTime. Mã token duy nhất được giữ nguyên khi thử lại cùng nội dung,
giúp tránh tạo trùng nếu mất phản hồi mạng. Không gửi cùng token cho nội dung
khác. Giới hạn 20 liên kết trên mỗi hồ sơ/sản phẩm.

Danh sách/chi tiết chỉ trả liên kết của hồ sơ hiện tại; ADMIN/MANAGER có thể
đọc tất cả. Chia sẻ `/r/{token}` trên frontend. Trang này gọi
`GET /api/v1/referrals/resolve/{token}` không cần đăng nhập, sau đó mở
`/products/{productId}?ref={referralLinkId}`. Backend kiểm tra lại hạn dùng,
trạng thái liên kết, hồ sơ, sản phẩm và gian hàng. Không chuyển tới URL do
client cung cấp.

Luồng này hỗ trợ tạo, lưu, sao chép, tải QR và mở sản phẩm. API Spring hiện
chưa triển khai ghi lượt nhấp/khách duy nhất, gắn mã giảm giá/chiến dịch/UTM
vào liên kết hoặc tự đưa `ref` vào đơn hàng khi thanh toán. Vì vậy không dùng
việc tạo/mở liên kết thành công để kết luận hoa hồng đơn hàng đã được ghi nhận.

Sau khi cập nhật mã, Stop rồi Run lại product-service (8082),
promotion-affiliate-service (8085) và scanms-gateway (8080). Mỗi cổng chỉ chạy
một tiến trình. Vite tự nạp lại cấu hình; tải lại trang nếu còn giao diện cũ.
Local dùng `ddl-auto=update`; khi dùng validate/none cần chạy SQL bổ sung trong
`src/main/resources/db/manual/20261021_referral_link_details.sql` trước.
Không tự tạo sản phẩm hoặc duyệt hồ sơ thật để lấp trạng thái trống.

Kiểm thử backend: `mvn -pl product-service,promotion-affiliate-service,scanms-gateway test`.
Kiểm thử giao diện trong SCANMS_FE:
`GHN_UI_TEST_URL=http://localhost:5173 node --test tests/referral-create-modal.browser.test.mjs tests/marketing-create-link.browser.test.mjs`.
Các kiểm thử trình duyệt chặn API và dùng dữ liệu giả lập, không ghi dữ liệu thật.
