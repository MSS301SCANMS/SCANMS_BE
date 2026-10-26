# Product / Seller & Platform Voucher — 26/10/2026

## Thay đổi sau rà soát

### Store, catalog và rating (6.1–6.3)

- Giữ luồng StoreApplication submit → manager approve/reject → Store APPROVED và cấp SHOP_OWNER; APPROVED là trạng thái store được hoạt động trong contract hiện tại.
- Private GET Store/Product/Image/Variant giới hạn theo shop sở hữu; manager và các internal role được đọc theo contract. API public vẫn phục vụ catalog. Bỏ cache toàn cục trên danh sách private để tránh trả dữ liệu shop khác.
- Shop SUSPENDED đọc được dữ liệu của mình nhưng không ghi Product/Image/Variant/Livestream.
- Catalog dùng ProductImage/ProductVariant làm nguồn ảnh và tồn kho. Không lấy sizesStock/imageRefs legacy để quyết định có thể mua. Sản phẩm không có variant ACTIVE với giá dương và tồn kho sẽ không mua được.
- Giữ kiểm tra Feedback theo customer + OrderItem đã thanh toán/giao, chống trùng và loại feedback ẩn/xóa khỏi rating. Chính sách hiện tại không cho tạo lại review đã xóa của cùng OrderItem/customer.

### Livestream (6.4)

- OpenFeign gọi `/referrals/livestream-access` để xác minh KOL APPROVED và đã chấp nhận hợp tác với store; không đọc hồ sơ KOL private bằng token shop.
- Phản hồi lời mời chỉ dành cho đúng KOL được mời. Lưu `participantRefs.collaboratorResponses[collaboratorId]` riêng từng người; không nhận quyết định consent do seller tự gán.
- Create/update/start/end/respond khóa bản ghi khi thay đổi trạng thái; kiểm tra giờ tương lai khi đặt lịch, endsAt sau giờ bắt đầu, store/product hợp lệ và tất cả KOL đã chấp nhận trước khi start.
- Product lưu tổng lượt vào phòng, unique theo danh tính đã băm và peak concurrent. Retry cùng visit ID không tăng trùng. Dữ liệu băm phục vụ dedup nằm trong `Livestream.statistics` JSONB, không trả qua DTO public.
- Relay gửi metrics qua Gateway với JWT + HMAC có thời hạn. Product từ chối sự kiện thiếu chữ ký. Hàng đợi relay nằm trong bộ nhớ, nên restart trước khi gửi thành công có thể mất sự kiện đang chờ.

### Voucher / checkout (6.5)

- Giữ validation PLATFORM/STORE/PRODUCT/LIVESTREAM, ownership, thời gian, minimum, cap, usage và allocation theo variant.
- Private GET voucher chỉ trả mã thuộc store của người gọi; mã public dùng endpoint public riêng. Manager/internal role đọc toàn bộ theo contract.
- Order FinanceDispatch có thêm action `cancel`, kể cả sau lần commit resources trước đó. Action gọi Product và Voucher bằng lệnh idempotent và được retry khi lỗi.
- Product `/inventory/reservations/{orderId}/cancel` trả tồn kho đã commit chỉ khi Order xác nhận cả đơn hủy trước giao.
- Voucher `/checkout-vouchers/{orderId}/cancel` giải phóng RESERVED hoặc kiểm tra điều kiện rồi khôi phục USED. Voucher còn hiệu lực → RESTORED; hết hiệu lực → CANCELLED. Return sau giao không khôi phục voucher.
- Eligibility kiểm tra tất cả seller order cùng lịch sử `deliveredAt` trên seller/shipment; không chỉ dựa vào trạng thái hiện tại.
- Đây là xử lý tồn kho/voucher khi Order domain đã chuyển cả đơn sang CANCELLED. Không bổ sung API/UI hủy đơn hoặc cơ chế hoàn khoản thanh toán mới thuộc Order/Payment.

## Chạy sau khi cập nhật code

1. Sao lưu Product DB rồi chạy `product-service/src/main/resources/db/manual/20261026_livestream_statistics.sql` trước khi Product khởi động với `JPA_DDL_AUTO=validate`.
2. Đặt cùng `LIVE_METRICS_SECRET` ngẫu nhiên dài ít nhất 32 ký tự ở `product-service/.env` và `livestream-realtime/.env`. Không đưa vào FE hoặc Git.
3. Restart Product, Order, Promotion/Affiliate để nạp code mới. Chạy relay bằng `npm.cmd start` trong `livestream-realtime`.

Máy hiện tại đã sao lưu, áp dụng migration hai lần để kiểm tra tính idempotent và cấu hình khóa local. Giữ nguyên 24 products, 96 variants, 69 images, 1 feedback, 1 livestream. Không có service IntelliJ của người dùng bị tắt để chạy QA.

## Bằng chứng kiểm thử

| Lần chạy | Kết quả |
|---|---|
| Toàn bộ Product | 60 bài, 0 fail/error, 1 migration test bỏ qua khi không có PostgreSQL QA |
| Toàn bộ Order | 67 bài, 0 fail/error, 1 bài PostgreSQL tùy chọn bỏ qua |
| Toàn bộ Promotion/Affiliate trước bổ sung test private read | 66 bài, 0 fail/error, 1 migration test bỏ qua |
| Inventory trên PostgreSQL QA | 6 bài đạt, gồm migration, concurrency, cancel và idempotency |
| Voucher checkout trên PostgreSQL QA + private read | 16 bài đạt, gồm migration, voucher đôi, expired restore, ownership |
| Node realtime signaling | Đạt: JWT identity, host consent, phòng riêng, chat, pin, end và gửi signed metrics |
| Gateway + Product + PostgreSQL clone QA | Đạt: catalog 24 sản phẩm, CRUD/ownership, lịch live, start/end, signed view và dedup |

Gateway QA chạy trên cổng riêng 18080/18082, database clone riêng và JWKS/User fixture ký RS256. Không coi bài này là test đăng nhập Keycloak thật. Bản clone và tiến trình QA đã được dọn; dữ liệu nhóm không bị ghi test.

Log ở workspace `.validation/scope-fix-tests-pass3.log`, `scope-fix-postgres-tests.log`, `scope-fix-realtime-tests.log`, `scope-gateway-smoke-result.json`.

## Chưa thể xác nhận tích hợp ngoài phạm vi

- User Service đang chạy trả 503 khi đăng nhập vì cấu hình xác thực. Cần đồng bộ cấu hình local với Keycloak đang dùng rồi chạy lại ma trận role thực. Không sửa realm/đọc secret bằng quyền admin trong đợt QA này.
- AI giữ interface/impl + OpenFeign, không truy cập persistence AI; test contract success/error đạt. Chưa thử model thật của Nam vì chưa có model/runtime, theo yêu cầu ưu tiên phần Thịnh.
- Không khẳng định toàn bộ checkout thanh toán thật/hoàn tiền thật, manager cấp role trên Keycloak thật hoặc hoa hồng production đã được nghiệm thu chỉ từ các bài QA trên.
