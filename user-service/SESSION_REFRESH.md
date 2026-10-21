# Duy trì phiên đăng nhập

Cấu hình realm trong dự án có accessTokenLifespan 300 giây, idle session 1800
giây và session tối đa 36000 giây. Frontend phải dùng refresh token thay vì tăng
thời hạn access token. Các giá trị runtime vẫn do realm Keycloak đang chạy quản lý.

`POST /api/v1/auth/refresh-token` công khai ở gateway và user-service, nhận
`{"refreshToken":"..."}`. Client không gửi Authorization đã hết hạn. Keycloak
kiểm tra refresh token và trả access/refresh token mới. `invalid_grant` trả 401;
lỗi cấu hình client/kết nối nhà cung cấp trả 503 và không tiết lộ phản hồi chứa
thông tin nội bộ.

Frontend lưu refresh token và hạn dùng từ cả đăng nhập mật khẩu lẫn Google,
gia hạn khi gọi API hoặc trở lại tab. Các lời gọi chung, ví và checkout dùng
cùng luồng gia hạn. Web Locks gom yêu cầu giữa nhiều tab; một lời gọi bị 401 chỉ
được thử lại một lần. Gia hạn không đổi tài khoản, workspace, dữ liệu form hay
tải lại trang. Phản hồi cũ sau khi đăng xuất/đổi tài khoản không được khôi phục
phiên cũ. Mất mạng/503 giữ phiên để có thể thử lại. Khi refresh token bị từ chối,
web xóa phiên và chuyển đến login kèm đường dẫn quay lại.

Sau khi cập nhật, Stop/Run lại user-service và scanms-gateway. Phiên đăng nhập
cũ chỉ có access token nên cần đăng nhập lại một lần để nhận refresh token.
Không sửa dữ liệu tài khoản hay thay đổi thời hạn realm thật để kiểm thử.

Kiểm thử backend: `mvn -pl user-service,scanms-gateway test`.
Kiểm thử frontend trong SCANMS_FE:
`GHN_UI_TEST_URL=http://localhost:5173 node --test tests/session-refresh.browser.test.mjs`.
Browser dùng API giả lập; backend dùng provider giả lập và database H2 riêng.
