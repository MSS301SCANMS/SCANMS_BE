# Module tài chính của Tuấn

API nghiệp vụ: `http://localhost:8080/api/v1/finance`. Gateway chuyển tới payment-service (8084).
Webhook payOS: `POST /api/v1/payments/webhooks/payos`. Webhook công khai nhưng kiểm tra HMAC; URL quay lại trình duyệt không quyết định thanh toán thành công.

## Chức năng đã triển khai

| Nhóm | Hành vi |
| --- | --- |
| Ví | CUSTOMER, STORE, COLLABORATOR, PLATFORM; tạo ví số dư 0, kiểm tra chủ sở hữu, phân trang ledger; Admin/Manager tạm khóa/mở/đóng ví không còn tiền |
| Ledger | TOP_UP, ORDER_PAYMENT, REFUND, SELLER_SETTLEMENT, KOL_COMMISSION, PLATFORM_FEE, WITHDRAWAL, HOLD, RELEASE; số dư trước/sau cả hai ngăn, khóa DB, chống trùng key, rollback chung với giao dịch nghiệp vụ |
| Thanh toán | Tạo link/QR payOS từ payableVnd của Order; top-up; thanh toán bằng ví; kiểm tra trạng thái, hủy, callback; unique activeOrderRef ngăn hai lần thanh toán hoạt động cho một Order |
| Hoàn tiền | Đọc refund-basis đã kiểm tra từ Order, ghi có ví khách, key theo ReturnRequest; Order tính số tiền từ giá đã trả và số lượng hoàn, không nhận số tiền từ FE |
| Hoa hồng | Đọc commission-basis từ Affiliate; kiểm tra trạng thái và thời hạn giao hàng/trả hàng với Order; ghi có ví KOL một lần |
| Đối soát | Đơn shop hoàn tất, đã giao và hết cửa sổ trả hàng ít nhất 14 ngày; không còn return chưa xử lý; trừ hoàn tiền, giảm giá shop, phí và hoa hồng; cộng trợ giá nền tảng; snapshot bất biến; PAID là ghi có ví shop |
| Phí | Admin tạo/sửa bản nháp, kích hoạt/ngừng; phần trăm BigDecimal, tiền Long VND; giới hạn min/max; khoảng hiệu lực không chồng nhau; làm tròn HALF_UP; quote và phiên bản được chụp lại |
| Ngân hàng | STORE/COLLABORATOR đăng ký; AES-256-GCM, API chỉ trả số che; Admin/Manager xác minh; chủ sở hữu ngừng dùng; rút tiền chỉ tới tài khoản đã xác minh cùng chủ ví |
| Rút tiền | REQUESTED chưa giữ tiền; APPROVED chuyển available→held; PROCESSING lưu trước khi gọi ngân hàng; SUCCESS trừ held và ghi phí nền tảng; FAILED/REJECTED giải phóng held; mất phản hồi giữ held để đối soát, retry cùng referenceId/idempotency key |
| Đồng bộ | Outbox cùng transaction ghi tiền; gửi lại paid/refunded/credited bằng service account; Admin thấy sự kiện chờ và yêu cầu retry |
| Ngày nghỉ | Chỉ hoãn chuyển ngân hàng; không hoãn ghi có ví/hoa hồng. Cuối tuần và BANK_CLOSED_DATES; lịch hẹn theo Asia/Ho_Chi_Minh |

Chưa có cấu hình phí ACTIVE thì quote ghi rõ `NO_ACTIVE_FEE`, giá trị 0. Phí dịch vụ và phí nền tảng được ghi chung vào ví PLATFORM bằng ledger PLATFORM_FEE, breakdown giữ riêng từng khoản.
Ví FROZEN chặn khoản trừ mới/HOLD nhưng cho phép nhận credit, RELEASE và hoàn tất WITHDRAWAL đã giữ. Chỉ đóng ví sau khi đã đối soát các nghĩa vụ còn có thể hoàn tiền.

## Cấu hình để chạy thật

1. Khởi chạy PostgreSQL các service, Keycloak, user/product/order/promotion/payment và gateway. Cổng frontend 5173. Spring nhận biến môi trường của tiến trình; `.env.finance.example` chỉ là mẫu, không tự được nạp.
2. Cấu hình payOS payment keys. Payout keys dùng kênh chi hộ riêng đã được cấp quyền. Đăng ký webhook HTTPS công khai trỏ về gateway. Giữ khóa ở BE.
3. Tạo `BANK_ENCRYPTION_KEY` từ 32 byte ngẫu nhiên, base64. Giữ ổn định và quản lý sao lưu khóa cùng dữ liệu mã hóa; thay khóa cần giải mã rồi mã hóa lại dữ liệu bằng quy trình riêng.
4. Keycloak public client `scanms-fe`: Standard Flow bật, PKCE S256, redirect `http://localhost:5173/auth/keycloak/callback`, Web Origins `http://localhost:5173`. Không đặt client secret ở FE.
5. Keycloak confidential client `payment-service`: bật Service Accounts, gán role `PAYMENT_INTERNAL`; cấu hình PAYMENT_SERVICE_TOKEN_URL/CLIENT_ID/CLIENT_SECRET. Outbox chưa có cấu hình sẽ giữ sự kiện, không giả vờ đồng bộ thành công.
6. JWT realm/client roles: USER, KOL_CTV, SHOP_OWNER, ADMIN, MANAGER. Alias SYSTEM_ADMIN/SYSTEM_MANAGER/SHOP_MANAGER/COLLABORATOR hỗ trợ ở payment. Hồ sơ `/users/me` phải có identitySubject trùng JWT sub và status ACTIVE; KOL phải có CollaboratorProfile APPROVED/ACTIVE; shop phải có ownerUserId đúng User UUID.
7. Gateway `FRONTEND_ORIGINS` hỗ trợ danh sách origin cách nhau dấu phẩy. Triển khai HTTPS thì thay URL mẫu tương ứng.
8. Với DB cũ: sao lưu, kiểm tra dữ liệu trùng và chạy `db/finance-upgrade.sql` trong cửa sổ triển khai. Script chưa được chạy trên DB thật. Không tự điền số dư, chuyển trạng thái tiền hoặc xóa ledger cũ để làm migration qua.

## API cho dịch vụ nguồn

- Order: `GET /api/v1/finance/refund-basis/{returnId}`, `GET /api/v1/finance/settlement-basis/{sellerOrderId}`. Ack bằng `POST /api/v1/finance/orders/{id}/paid`, `/returns/{id}/refunded`.
- Affiliate: `GET /api/v1/finance/commission-basis/{commissionId}`, `GET /api/v1/finance/commissions-total?orderItemIds=...`. Ack bằng `POST /api/v1/finance/commissions/{id}/credited`.
- Payment tiếp nhận lệnh `POST /finance/refunds/{id}`, `/commissions/{id}/credit`, `/settlements` từ Admin/Manager hoặc PAYMENT_INTERNAL. Ghi có settlement và duyệt/chuyển withdrawal do Admin/Manager.
- Lệnh `execute`/`reconcile` withdrawal có thể gọi từ màn hình quản trị. Worker tự xử lý các yêu cầu APPROVED/PROCESSING đã đến scheduledFor mỗi phút, dùng service-account token được JwtDecoder xác minh và role PAYMENT_INTERNAL. Không có token hợp lệ hoặc kênh chi hộ thì không chuyển. Đặt AUTOMATIC_PAYOUTS=false nếu cần chế độ chuyển thủ công.

Contract nguồn ở Order/Affiliate gọi trực tiếp giữa service, không đi qua gateway `/finance/**` (namespace gateway này dành cho payment). Tất cả ack đều yêu cầu PAYMENT_INTERNAL. Các POST CRUD cũ tạo trạng thái/số tiền tài chính đã được hạn chế: raw payment/bank/withdrawal/settlement/fee POST bị từ chối; source Order và Commission/Collaborator POST chỉ Admin hoặc service account đúng miền. API checkout/return/commission nghiệp vụ phải cung cấp các bản ghi nguồn hợp lệ.

## Ranh giới và kiểm chứng

Payment không tạo sản phẩm, trừ tồn kho, áp voucher hay tự tính chính sách hoa hồng. Skeleton Order hiện chưa có API checkout nghiệp vụ tương thích payload giỏ hàng FE; Affiliate chưa tự tạo/finalize commission từ shipment/partial return. Đây là phần Thắng/Nam/Thịnh phải nối. Module nhận Order UUID và dữ liệu đã xác nhận; checkout FE đã chuyển phần thanh toán sang finance API khi source trả Order UUID, báo rõ lỗi nếu chưa có UUID. Không dùng mã đơn hiển thị thay UUID, không lấy tổng tiền từ FE.

Kiểm thử dùng H2 PostgreSQL mode, bean giả lập contract/provider và MockMvc; kiểm tra khóa/retry/rollback, giữ/giải phóng, snapshot phí, chữ ký, mã hóa, lịch ngân hàng và quyền HTTP. Cần thử PostgreSQL thật và payOS được cấp quyền trước khi triển khai giao dịch thật. Bộ test toàn FE còn các test cũ phụ thuộc backend NestJS không có trong workspace và các assertion source đã cũ; xem báo cáo bàn giao ở thư mục workspace.

Tài liệu payOS được đối chiếu: https://payos.vn/docs/api/ và https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/.
