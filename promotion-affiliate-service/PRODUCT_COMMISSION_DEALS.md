# Hoa hồng cơ bản và deal theo sản phẩm

## Mức mặc định

Promotion Affiliate tự tạo chính sách PLATFORM **10%**, thời hạn trả hàng 14 ngày,
khi chưa tồn tại bất kỳ chính sách PLATFORM nào. Không sửa, nhân bản hoặc kích hoạt
lại chính sách mà quản trị đã chỉnh/tắt. `COMMISSION_DEFAULT_POLICY_ENABLED=false`
tắt bước khởi tạo này; không xóa chính sách hiện có.

Chính sách được resolver lựa chọn như trước: COLLABORATOR → LIVESTREAM → PRODUCT
→ STORE → PLATFORM. Deal là thỏa thuận riêng cho đúng **KOL + sản phẩm + shop**.

## Luồng sử dụng

1. KOL mở **Tiếp thị liên kết và phát trực tiếp → Liên kết tiếp thị → Deal hoa hồng
   sản phẩm → Đề xuất tăng hoa hồng**.
2. Chọn sản phẩm và tăng thêm **1, 2 hoặc 3 điểm phần trăm**; nhập lời đề xuất.
   Với mức cơ bản 10%, tổng là 11%, 12% hoặc 13%. KOL không tự nhập tổng tùy ý.
3. Chủ shop mở **Hợp tác KOL → Deal hoa hồng sản phẩm → Xem đề xuất**.
   Xem cơ bản, mức tăng, tổng và nội dung rồi **Duyệt** hoặc **Từ chối**.
4. Đề xuất PENDING không thay đổi mức đang hưởng. Một KOL chỉ có một đề xuất đang
   chờ trên mỗi sản phẩm. KOL có thể thu hồi đề xuất đang chờ.
5. Duyệt deal mới thay thế deal cũ, không cộng dồn bonus. Shop có thể kết thúc
   deal cho đơn mới; lịch sử các mốc hiệu lực vẫn được giữ.
6. Liên kết tiếp thị và cửa sổ tạo link hiển thị tỷ lệ thực từ API. Không sửa token
   link cũ và không cần tạo link mới sau mỗi lần duyệt deal.

Nếu mức cơ bản đổi trong lúc đề xuất chờ, shop không thể duyệt âm thầm mức tổng
khác với đề xuất. Shop từ chối và KOL gửi lại theo chính sách mới.

## Đơn hàng, hoàn tiền và thời gian

Order Item bổ sung `placedAt` kiểu Instant (UTC tuyệt đối), do server tạo một lần,
không nhận từ checkout FE. Cột mới tự thêm khi Order Service khởi động với
`ddl-auto=update`. Đơn cũ thiếu mốc UTC rõ ràng không bị tự áp bonus mới; không
suy đoán múi giờ từ `LocalDateTime` cũ.

Finance chỉ chọn deal đã duyệt và còn hiệu lực **tại placedAt của đơn**. Tỷ lệ tổng,
mức cơ bản, điểm tăng và mã deal được snapshot trong Commission khi khởi tạo.
Đổi/kết thúc deal hoặc sửa chính sách không thay đổi Commission đã ghi nhận.
Nguồn order/seller store không xác minh được thì trả lỗi để worker thử lại,
không tự tính mức cơ bản thấp hơn và bỏ qua deal.

Ví dụ tiền hàng thực trả 299.000đ, cơ bản 10% + deal 2 điểm = 12%:

- Ban đầu: 35.880đ, chờ đủ điều kiện và hết hạn trả hàng.
- Hoàn toàn bộ thành công: 0đ; tỷ lệ snapshot vẫn 12%.
- Đã ghi có: giữ giao dịch gốc, nguồn đối soát yêu cầu giao dịch thu hồi riêng.
- Retry không khởi tạo lại khoản tiền hoặc thay tỷ lệ đã chốt.

Chi tiết ở **Ví & Rút tiền → Hoa hồng từ đơn giới thiệu → Chi tiết** hiển thị tỷ lệ
đã chốt, cơ bản + deal, hoa hồng gốc, số đã giảm và giao dịch liên quan.

## API

Các endpoint có xác thực dưới `/api/v1/referrals/commission-deals`:

- `GET /mine`: đề xuất của KOL hiện tại đã được duyệt hồ sơ.
- `GET /shop`: chỉ các gian hàng người dùng hiện tại sở hữu.
- `GET /rate?productId=...`: mức cơ bản và deal đang hiệu lực cho KOL hiện tại.
- `POST /`: `{productId, extraPoints:1|2|3, message}`.
- `POST /{id}/respond`: chủ shop gửi `{decision:APPROVED|REJECTED|ENDED,response}`.
- `POST /{id}/cancel`: KOL thu hồi đề xuất đang chờ của chính mình.

Không nhận storeId/collaboratorId/tổng tỷ lệ từ FE để cấp quyền hoặc duyệt.
Khóa profile rồi deal để xử lý đồng thời, chống nhiều yêu cầu pending và duyệt lặp.
Mốc hiệu lực deal dùng độ chính xác microsecond tương thích PostgreSQL.

## Khởi động và kiểm tra

Khởi động lại **OrderServiceApplication** và **PromotionAffiliateServiceApplication**
để có cột mốc đơn UTC, bảng deal và endpoint mới; FE Vite tự cập nhật.
Gateway đã có route `/referrals/**`, không cần thêm route cho deal.

Kiểm tra ở `ProductCommissionDealIntegrationTest`, `CheckoutVoucherIntegrationTest`
và `CheckoutIntegrationTest`; FE `product-commission-deals.browser.test.mjs`.
Các test bao gồm duyệt +1/+2/+3, pending không có hiệu lực, giới hạn điểm, quyền
shop/KOL, không áp hồi tố, thay thế/kết thúc không cộng dồn, hoàn toàn bộ trước
và sau ghi có, retry và lịch sử tỷ lệ. Bài test PostgreSQL chỉ dùng DB QA riêng.
