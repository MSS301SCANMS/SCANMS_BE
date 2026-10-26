# Video ngắn và hoa hồng

## Luồng

1. Manager duyệt hồ sơ KOL/KOC. Shop và KOL chấp nhận lời mời hợp tác. Shop cấu hình chính sách hoa hồng còn hiệu lực và tỷ lệ lớn hơn 0 cho sản phẩm; nếu thiếu chính sách, API từ chối đăng video tiếp thị và giải thích rõ.
2. Trong **KOL → Video ngắn**, KOL tải video MP4/WebM/MOV tối đa 100 MB, tối đa 180 giây và gắn 1–5 sản phẩm đang bán của cùng shop hợp tác.
3. Video được đăng công khai ngay, không cần shop duyệt thêm. Người đăng có thể ẩn video; manager có thể ẩn qua API.
4. Người mua mở **Video cùng KOC** ở trang chủ, xem video, bật/tắt tiếng và bấm sản phẩm để mua qua trang sản phẩm/checkout hiện tại.
5. Chỉ lượt bấm sản phẩm mới ghi nguồn giới thiệu. FE giữ lượt bấm gần nhất theo từng sản phẩm, qua các tab trên cùng trình duyệt, tối đa 24 giờ. Máy chủ lưu thời gian thực và kiểm tra mã lượt bấm khi đặt hàng.
6. Checkout lưu referralLinkId đã xác minh và ruleSnapshot gồm referralClickId, sourceVideoId, collaboratorId, clickedAt. Một sản phẩm chỉ nhận một nguồn trong đơn. Không nhận tỷ lệ hoa hồng do FE gửi.

## Cách tính tiền

`Hoa hồng = tiền hàng thực giữ lại sau giảm giá/hoàn tiền × tỷ lệ commission policy đã chụp`

Không tính phí ship vào hoa hồng. Ví dụ giá 300.000đ, giảm tiền hàng 30.000đ, tỷ lệ 10% → hoa hồng 27.000đ; voucher freeship không làm thay đổi cơ sở tiền hàng. Hệ thống hiện có chọn policy theo COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM. Tỷ lệ được chụp khi khởi tạo commission sau thanh toán, không phải tỷ lệ cố định 10% cho mọi shop.

Commission chờ đối soát theo luồng tài chính hiện có: thanh toán thành công, đơn hoàn tất, hết thời hạn đổi trả và không còn tranh chấp/yêu cầu trả hàng chưa xử lý. Hoàn hàng làm giảm cơ sở tính theo sản phẩm; xử lý lại cùng đơn không tạo tiền hoa hồng lần hai. KOL tự mua hàng không được ghi hoa hồng. Lượt bấm hết 24 giờ không chặn mua hàng, chỉ bỏ ghi hoa hồng. Mã sai sản phẩm hoặc dịch vụ xác minh lỗi sẽ yêu cầu thử lại, tránh âm thầm ghi sai người.

Đây là hoa hồng tiếp thị do shop trả theo policy; phí nền tảng dùng cấu hình tài chính hiện tại, không tự thêm tỷ lệ mới. Thanh toán tiếp tục dùng Ví SCANMS/PayOS theo cấu hình đang có.

## Dịch vụ và triển khai

- User: `/api/v1/upload/short-video`, `/api/v1/upload/short-video/verify`. Cloudinary lưu media; User kiểm tra metadata thời lượng và quyền sở hữu đường dẫn tài khoản. Không lưu blob URL từ máy người đăng.
- Product: `ShortVideoService`/`ShortVideoServiceImpl`, MapStruct mapper, OpenFeign gọi Affiliate và User. Feed `GET /api/v1/public/short-videos?page=0`; tạo/list/ẩn video qua `/api/v1/short-videos`.
- Affiliate: xác minh KOL/hợp tác/liên kết tại `/api/v1/referrals/video-access`; ghi lượt bấm công khai `POST /api/v1/referrals/clicks`; endpoint xác minh lượt bấm chỉ cho ORDER_INTERNAL.
- Order: `CheckoutAttribution`/`CheckoutAttributionImpl` dùng OpenFeign xác minh lượt bấm trước khi giữ tồn kho; lưu nguồn vào từng OrderItem. Một bare referralLinkId không có mã lượt bấm không đủ để ghi hoa hồng 24 giờ.
- Gateway: chuyển tiếp public feed, short-videos và referrals, chỉ cho phép ghi lượt bấm công khai; đăng/ẩn video vẫn yêu cầu đăng nhập.

Trước khi khởi động với `JPA_DDL_AUTO=validate`, chạy các migration **chỉ thêm bảng/index**:

- Product DB: `product-service/src/main/resources/db/manual/20261026_short_videos.sql`.
- Affiliate DB: `promotion-affiliate-service/src/main/resources/db/manual/20261026_referral_clicks.sql`.

Tài khoản app cần SELECT/INSERT/UPDATE/DELETE trên hai bảng mới; không cần quyền schema owner. Khởi động lại User, Product, Affiliate, Order và Gateway sau build. Không xóa volume/database và không tạo lại catalog để chạy video.

## Kiểm tra

- `ShortVideoIntegrationTest`: đăng ngay, idempotent retry, quyền ẩn, ẩn khỏi feed, sản phẩm trùng/khác shop/ngừng bán.
- `ShortVideoReferralServiceTest`: timestamp máy chủ, thời hạn 24 giờ, liên kết video/sản phẩm chính xác, tự mua và hợp tác shop.
- `CheckoutIntegrationTest`: lưu đúng nguồn qua OpenFeign, không trùng khi retry, hết hạn vẫn mua được, mã sai không giữ tồn kho; hồi quy voucher đôi và cơ sở hoàn tiền.
- FE `referral-attribution.test.mjs`, `short-videos.browser.test.mjs`: lượt bấm gần nhất, hết hạn, lỗi/retry feed, mobile, nguồn theo sản phẩm khi điều hướng.

Các test Cloudinary/PayOS không thực hiện tải nội dung hoặc chuyển tiền thật. Cần cấu hình Cloudinary hợp lệ để người dùng tải video thực tế; PayOS vẫn phụ thuộc thông tin kênh thanh toán và webhook của môi trường.
