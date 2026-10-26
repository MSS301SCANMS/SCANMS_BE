# Chạy thử và kết nối payOS

Chưa có tài khoản payOS vẫn kiểm thử được đơn hàng bằng ví local. Bật profile `local`, `PAYMENT_SANDBOX_ENABLED=true`, `AUTOMATIC_PAYOUTS=false` trong cấu hình riêng của payment-service. Vào `/customer/wallet`, nạp Demo rồi thanh toán đơn bằng ví. Đây là tiền kiểm thử, không có chuyển khoản ngân hàng.

## Khi có kênh payOS

1. Tạo tài khoản tại https://my.payos.vn, xác thực cá nhân/doanh nghiệp và tạo kênh thanh toán theo https://payos.vn/docs/api/.
2. Điền ba biến dưới đây vào `be/payment-service/.env` local (không commit, không đưa vào FE):

   ```dotenv
   PAYOS_CLIENT_ID=
   PAYOS_API_KEY=
   PAYOS_CHECKSUM_KEY=
   FRONTEND_URL=http://localhost:5173
   ```

3. Chạy gateway và payment-service. Cấp một URL HTTPS công khai chuyển tới gateway local 8080. Webhook đúng là `https://<host>/api/v1/payments/webhooks/payos`; URL localhost không nhận được callback từ payOS.
4. Kiểm tra script từ thư mục `be`:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Configure-PayosWebhook.ps1 -WebhookUrl "https://<host>/api/v1/payments/webhooks/payos"
   ```

   Lệnh trên chỉ kiểm tra cấu hình local. Khi đã sẵn sàng đăng ký/thay webhook của kênh, thêm `-Register`. Script chỉ ghi `PAYOS_WEBHOOK_URL` local sau khi payOS xác nhận thành công.

5. Restart payment-service. `GET /api/v1/finance/capabilities` phải trả `paymentAvailable=true`. Tạo đơn rồi chọn payOS: backend tạo link/QR, người mua thanh toán, backend xác minh chữ ký/trạng thái và đồng bộ đơn hàng bằng outbox.
6. Thử số tiền nhỏ với kênh đã được phép sử dụng. Kiểm tra cả thành công, hủy, hết hạn và webhook lặp. Callback/return URL trên trình duyệt không được tự ghi nhận đơn đã trả tiền.

`paymentAvailable=true` cho biết có đủ ba khóa local; chỉ giao dịch với payOS thật và callback hợp lệ mới xác nhận kết nối hoạt động. Nạp Demo của SCANMS và môi trường test của payOS là hai cơ chế khác nhau. Không bật nạp Demo trên production.

Nguồn chính thức: https://payos.vn/docs/, https://payos.vn/docs/api/.
