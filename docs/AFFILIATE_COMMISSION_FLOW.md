# Luồng mua qua KOL, trả hàng và hoa hồng

## Hành vi

- Trang sản phẩm lưu mã giới thiệu theo từng sản phẩm trong tab trình duyệt. Cả mua ngay và mua từ giỏ hàng gửi `items[].referralLinkId` khi checkout. Mua trực tiếp từ livestream giữ thêm `sourceLivestreamId`.
- Order xác minh mã bằng tài khoản dịch vụ: đúng sản phẩm, link còn hoạt động, KOL đã được duyệt; không nhận đơn tự mua qua link của chính khách đó. Mã được lưu vào từng `OrderItem`. Thử lại checkout dùng cùng khóa không tạo thêm đơn.
- Khi đơn đã thanh toán, Affiliate khởi tạo hoa hồng `PENDING` theo tiền hàng thực trả sau giảm giá, không tính phí giao hàng. Tỷ lệ được chụp lại tại thời điểm tính, không đổi theo chỉnh sửa policy về sau. Lệnh khởi tạo được gửi lại từ trạng thái đơn đã lưu nếu dịch vụ tạm ngắt.
- Chỉ chốt `ELIGIBLE` sau khi đơn hoàn tất, hết hạn trả hàng và không còn yêu cầu hoàn chưa xử lý. Worker Affiliate ghi có vào ví **COLLABORATOR**, không phải ví CUSTOMER. Giao dịch có khóa theo commission ID.
- Chỉ tiền **đã hoàn thành hoàn trả** mới giảm cơ sở tính. Hoàn toàn bộ tiền hàng của sản phẩm → hoa hồng sản phẩm đó bằng **0đ**, trạng thái `CANCELLED`. Không đổi tỷ lệ hoa hồng. Trả toàn bộ đơn nhiều sản phẩm thực hiện cho từng dòng được chọn; dòng không trả giữ nguyên hoa hồng.
- Nếu đã ghi có, Payment đối chiếu lại số tiền với Order/Affiliate và tạo giao dịch `COMMISSION_REVERSAL`, tham chiếu commission và giao dịch ghi có gốc. Không sửa/xóa giao dịch gốc. Khóa ví và khóa giao dịch ngăn trừ hai lần khi gửi đồng thời hoặc thử lại.
- Nếu KOL đã rút tiền, tiền thu hồi được thể hiện thành số dư âm; khoản thu nhập sau bù lại số âm. KOL không thể tạo yêu cầu rút vượt số dư khả dụng. Đây là điều chỉnh sổ cái, không tự thu tiền từ tài khoản ngân hàng KOL.
- Callback ghi có/thu hồi đến muộn không được khôi phục hoa hồng đã hủy. Outbox và worker gửi lại theo cùng tham chiếu khi mất kết nối.

## Giao diện

- Khách: **Gửi yêu cầu trả hàng**, chọn sản phẩm/số lượng và bằng chứng; gửi lại phần thất bại với cùng khóa, không gửi lại phần đã thành công.
- Chủ shop: **Xác nhận nhận hàng trả** sau khi kiện hàng đến; theo dõi chờ hoàn và đã hoàn tiền.
- KOL: `/collaborator/wallet` → **Hoa hồng từ đơn giới thiệu**, lọc trạng thái, **Cập nhật hoa hồng**, xem giao dịch thu hồi trong lịch sử ví. KOL chỉ đọc hoa hồng của chính mình.
- Bảng mới có bốn số tổng hợp, tìm kiếm theo sản phẩm/mã, phân trang và **Chi tiết** từng khoản: tiền hàng còn lại, tỷ lệ đã chốt, hoa hồng ban đầu, tiền đã giảm, số đã ghi nhận vào ví và tham chiếu giao dịch. Điện thoại hiển thị từng thẻ, không phải kéo bảng ngang.
- Phân biệt **đã giảm hoa hồng** (nguồn đơn hàng đã xác nhận hoàn) với **đã thu hồi khỏi ví** (sổ cái đã xác nhận). Dữ liệu lịch sử thiếu ảnh chụp hiện “Chờ đối chiếu”/“Chưa có dữ liệu gốc”, không tự điền 0 hoặc bịa số tiền ban đầu. Đồng bộ không phát sinh khoản thu hồi không được gắn mã giao dịch thu hồi giả.
- **Lấy Link Tiếp Thị** trong màn hình hợp tác Shop cũng phải gọi API `/referrals`, lưu theo tài khoản KOL và kênh. Thử lại dùng cùng token; QR dùng đúng link đã lưu. Đã bỏ `ref=kol` và các tỷ lệ 10%/10–21% gán sẵn khi chưa có dữ liệu chính sách.
- Quản trị: `/admin/system-wallet` → **Đối soát hoa hồng KOL**, **Ghi có hoa hồng** khi đủ điều kiện hoặc **Đồng bộ thu hồi** khi cần thu hồi. Hai nút đều có bước xác nhận; backend kiểm tra lại nguồn và quyền.
- Bình thường việc ghi có/thu hồi chạy tự động theo chu kỳ cấu hình; nút quản trị dùng để xử lý lại, không cho phép tự nhập số tiền hoa hồng.

## Cấu hình local và triển khai

1. Áp dụng `payment-service/db/commission-reversal-upgrade.sql` cho payment DB, trong transaction. Script có thể chạy lại, giữ các loại giao dịch cũ và thêm loại thu hồi mới.
2. Cấu hình `FINANCE_TOKEN_URL`, `ORDER_SERVICE_CLIENT_ID/SECRET`, `AFFILIATE_SERVICE_CLIENT_ID/SECRET`, `PAYMENT_SERVICE_TOKEN_URL` và `PAYMENT_SERVICE_CLIENT_ID/SECRET`. Các service account cần đúng role lần lượt: `ORDER_INTERNAL`, `AFFILIATE_INTERNAL`, `PAYMENT_INTERNAL`.
3. Với Docker Keycloak local sẵn có ở cổng 8180: `python scripts/setup-local-affiliate-identity.py`. Script dùng bootstrap admin hiện có, giữ secret client hiện có, kiểm tra role trong token và chỉ lưu secret vào file `.env` được Git bỏ qua. Không đổi quyền người dùng hoặc cấu hình Google OAuth.
4. Build ba module rồi chạy `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/restart-local-affiliate-services.ps1`, hoặc dừng/chạy lại ba service tương ứng trong IntelliJ. Không chạy đồng thời hai bản cùng cổng 8083–8085.

Worker mặc định 60 giây. Khi kiểm tra hoàn tiền, chờ xác nhận hoàn và một chu kỳ đồng bộ tiếp theo; **Cập nhật hoa hồng** đọc lại trạng thái hiện tại.

## Kiểm chứng ngày 26/10/2026

- Kiểm thử bao gồm giữ attribution, link sai/tự mua, hoàn toàn bộ trước chốt, thu hồi sau ghi có, retry/concurrency, số dư âm, callback đến muộn và giới hạn dữ liệu theo KOL. Bổ sung trường hợp chốt hoa hồng khi worker điều chỉnh đến muộn, giữ ngày ghi có gốc và đồng bộ không phát sinh giao dịch thu hồi.
- FE build thành công. Kiểm thử trình duyệt và adapter bao gồm checkout gửi mã KOL, form trả hàng, xác nhận nhận hàng của shop, bộ lọc/tìm kiếm/phân trang/chi tiết trên desktop và điện thoại, xác nhận hai nút quản trị, tạo link/QR thật từ sản phẩm của Shop và từ công cụ tạo link.
- Kiểm tra schema API mới trên ba service và token gọi các API nguồn nội bộ trực tiếp sau khi khởi động lại. Route `/finance/**` trên gateway là của Payment; API nguồn Order/Affiliate dùng cổng nội bộ tương ứng, không gọi qua gateway. Migration đã áp dụng cho payment DB local.
- Các tình huống tài chính được kiểm tra bằng dữ liệu kiểm thử; không dùng đơn mới của người dùng để thực hiện payout ngân hàng thật.

### Rà soát bổ sung và thiết kế lại

- 88 kiểm thử BE đạt: Order 15, Payment 39, Affiliate 34. Các bộ checkout, refund reservation, finance ledger và affiliate calculation chạy trên PostgreSQL 17.11 trong container QA riêng; kiểm tra migration chạy lại hai lần. Các bộ kiểm thử còn lại dùng H2 hoặc mock theo cấu hình riêng. Không bỏ qua ca nào trong lần rà soát này.
- 17 kiểm thử FE đạt; production build thành công. Đã kiểm tra ảnh desktop, thẻ mobile, cửa sổ chi tiết, bộ lọc/tìm kiếm/phân trang, trạng thái lỗi và thử lại, xác nhận ghi có/thu hồi, link rút gọn và QR đã lưu, checkout gửi referral, trả hàng nhiều sản phẩm và xác nhận nhận hàng của Shop.
- Callback lặp giữ ngày ghi có đầu tiên. Callback sau hoàn trả phản hồi đúng `CANCELLED`; đối chiếu không tạo khoản trừ không được hiển thị thành một giao dịch thu hồi.
- Dịch vụ trong IntelliJ được giữ nguyên. Sau thay đổi controller, chạy lại `PromotionAffiliateServiceApplication` để nạp code BE mới; FE nhận thay đổi qua Vite. Không mở thêm bản Java chạy nền cùng cổng.
- Phạm vi xác nhận: nghiệp vụ và giao diện với dữ liệu kiểm thử, có lưu PostgreSQL thực. Chưa xác nhận giao dịch thanh toán/ngân hàng bên ngoài bằng tiền thật.
