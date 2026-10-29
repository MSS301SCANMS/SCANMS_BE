# Giá live, voucher và chat — 26/10/2026

## Luồng sử dụng

1. Shop và KOL đã được duyệt, hợp tác được chấp nhận, KOL nhận lời mời phiên. Shop bắt đầu phiên theo lịch.
2. Chủ shop mở phòng live → Host Studio → **Giá live & Voucher**.
3. Chọn sản phẩm, size/màu, giá live, số lượng, số phút → **Áp dụng & ghim sản phẩm**. Giá gốc của ProductVariant không bị sửa.
4. Người xem thấy giá live của phân loại được ưu đãi và các voucher sàn phù hợp. **Thêm giỏ/Mua ngay** mở chọn phân loại ngay trong phòng.
5. Shop chọn voucher có sẵn hoặc tạo mã STORE/LIVESTREAM. Chọn **Tung voucher ngay**, hoặc điền giờ để **Lên lịch tung voucher**. Thời điểm phát được lưu ở backend, không phụ thuộc việc shop giữ tab mở. Người xem đang ở phòng cập nhật trong chu kỳ khoảng 3 giây khi API bình thường.
6. Checkout lấy giá server và gợi ý voucher tốt nhất, tối đa một mã tiền hàng + một mã vận chuyển. Người dùng có thể chọn mã khác. Tổng thanh toán thay đổi sẽ bị từ chối thay vì âm thầm tạo đơn READY ở giá mới.

## Trách nhiệm và giới hạn

- Shop chỉ thiết lập giá và tung mã cho store mình sở hữu, đang APPROVED. KOL được mời/đã chấp nhận có quyền phát camera và ghim; shop điều phối riêng để không chiếm vị trí camera KOL.
- Shop tài trợ mã do shop tạo. Voucher PLATFORM được chọn từ danh sách sàn cho phép; shop không sửa mức giảm, điều kiện hay ngân sách sàn.
- Với voucher có sẵn, số lượt giới thiệu không phải một đợt reserve ngân sách mới, không làm tăng usage limit. Với mã tạo riêng cho phiên, quantity được dùng làm totalUsageLimit và mỗi customer tối đa một lần. Dừng một thông báo mã có sẵn không vô hiệu hóa mã gốc; hiệu lực gốc vẫn theo Promotion Service.
- Giá live lưu tại `Livestream.productRefs.offers`, lịch voucher tại `participantRefs.voucherDrops`; không tạo entity phụ hoặc thêm persistence phụ thuộc AI. DTO thường của livestream ẩn lịch voucher; endpoint commerce/manage dành cho owner xem lịch chưa phát.
- Slot ưu đãi được giữ cùng reservation tồn kho; quote không giữ slot. Release/cancel hợp lệ trả slot đúng một lần. Hai checkout đồng thời không vượt số lượng bán với giá ưu đãi. Khi hết quota/thời gian, giá mới được tính lại theo giá hiện tại; expectedPayableVnd chặn tạo đơn ở tổng chưa được khách xác nhận.
- Relay cấp proof ngắn hạn gắn với JWT subject và livestream sau khi vào phòng. Order chuyển proof và buyer subject qua OpenFeign tới Product; không thể lấy giá live chỉ bằng sửa tham số URL. Proof được gia hạn khi còn kết nối; không ghi vào fingerprint checkout hoặc snapshot đơn. Proof hiện có thời hạn tối đa 120 giây.
- Giỏ live tách key khỏi giỏ thường. Cùng một phân loại ở hai nguồn phải chọn một nguồn cho mỗi checkout; không tự chuyển toàn bộ giỏ thường sang giá live.
- Product DELETE là INACTIVE. Reservation hợp lệ đã tồn tại được retry bằng snapshot cũ kể cả sau khi ẩn sản phẩm; đơn đã tạo giữ tên, ảnh, SKU, size/màu và giá. Sản phẩm bị ẩn chặn tạo reservation mới. Thanh toán/giao hàng tiếp tục theo vòng đời Order hiện tại.
- Chat có ACK, giữ nội dung khi gửi lỗi, retry cùng message ID không trùng. KOL/shop vào sau nhận buffer tối đa 100 tin của phòng. Chat buffer và hàng đợi metrics nằm trong bộ nhớ relay, không phải lịch sử lâu dài.

## API và cấu trúc code

Controller → interface service → implementation. Request/response DTO riêng; MapStruct map dữ liệu offer/drop. Product dùng OpenFeign đọc Voucher; Order dùng OpenFeign quote/reserve inventory.

| Method | Đường dẫn `/api/v1` | Quyền / tác dụng |
|---|---|---|
| GET | `/livestreams/{id}/commerce` | Người xem: giá đang mở và mã đã tung |
| GET | `/livestreams/{id}/commerce/manage` | Owner: gồm lịch/ưu đãi đã dừng |
| POST | `/livestreams/{id}/offers` | Owner: đặt giá, quota, thời hạn; tự ghim |
| POST | `/livestreams/{id}/offers/{offerId}/stop` | Owner: dừng giá |
| POST | `/livestreams/{id}/voucher-drops` | Owner: tung ngay hoặc lưu lịch |
| POST | `/livestreams/{id}/voucher-drops/{dropId}/cancel` | Owner: dừng giới thiệu |
| POST | `/livestreams/{id}/pin` | Owner/KOL được phép: lưu sản phẩm ghim |
| POST | `/livestreams/{id}/purchase-access` | JWT viewer + chữ ký relay: cấp proof mua trong live |

API inventory nhận thêm buyerIdentitySubject và livePurchaseToken trong line. API checkout nhận expectedPayableVnd để kiểm tra tổng khách đang xác nhận. Các field mới giữ constructor tương thích cho caller cũ.

## Chạy local

### Sửa ghim và phân tách giá ngày 27/10

Chủ shop được kiểm tra ownership trước, không cần chờ xác minh hợp tác KOL để ghim.
KOL vẫn phải được mời và chấp nhận; người ngoài không được điều khiển phiên. Relay
chỉ broadcast sau khi Product lưu thành công. FE đọc đúng pinnedProductId trong
productRefs, không tự coi sản phẩm đầu tiên là đã ghim, và loại phản hồi poll cũ
khi thao tác ghim hoặc lưu giá vừa hoàn tất.

Giá live thuộc đúng phiên + variant trong productRefs.offers. Thay giá catalog
không ghi đè offer đang chạy; thay offer không sửa Product/ProductVariant. Checkout
thường và phiên khác dùng giá catalog; reservation đã tạo giữ snapshot. Ví dụ đã
test: catalog 329.000 → live 299.000; catalog đổi 349.000 thì live vẫn 299.000.

Thẻ giới thiệu, Host Studio, túi đồ và chọn phân loại dùng giá live còn hiệu lực;
các nhãn giảm 25%, giảm 150K, quà và freeship mẫu đã được bỏ khỏi túi đồ. Voucher
hiển thị dựa trên dữ liệu thật. Kết quả lưu offer cập nhật ngay tại shop, người
xem nhận qua polling. Lỗi API cũ/không tải được ưu đãi hiện thông báo để thử lại.

Product đang chạy từ IntelliJ cần Stop/Run lại để nhận các route /pin, /offers,
/commerce. Chỉ reload FE không cập nhật được controller trong JVM cũ.

Kiểm tra bổ sung: 7 LiveCommerceIntegrationTest đạt (tách giá, snapshot, quyền),
3 relay test đạt, browser shop/customer kiểm tra ghim/bỏ ghim, reload giữ ghim,
thay giá/auto-pin, túi đồ, chọn variant và voucher đạt. Fixture dùng cổng QA riêng,
không ghi vào database sản phẩm thật.

- Restart Product và Order để nhận các endpoint/contract mới. Không cần migration mới cho offers/drops vì dùng cột JSONB refs hiện có.
- Relay: `cd be/livestream-realtime; npm.cmd ci; npm.cmd start`, hoặc `scripts/Start-Livestream.ps1` tại `be`. Chỉ chạy một relay trên cổng 8090.
- Product và relay dùng cùng LIVE_METRICS_SECRET dài ít nhất 32 ký tự trong file .env local. Không đưa secret vào FE/Git.
- FE local dùng VITE_LIVE_SOCKET_URL; nếu chưa đặt, localhost tự chọn cổng 8090. Các môi trường khác phải cấu hình URL WSS phù hợp.
- Refresh hai màn hình customer/KOL sau khi có relay. Kiểm tra health chỉ xác nhận Node đang chạy; việc vào phòng còn cần JWT và API nghiệp vụ sẵn sàng.

## Kiểm thử

- Product suite trước sửa assertion numeric: 64 bài, một assertion so sánh Integer/Long của snapshot thất bại; còn lại không fail. Assertion được đổi sang so sánh JSON cùng nội dung, sau đó toàn bộ LiveCommerceIntegrationTest đạt.
- PostgreSQL QA riêng: 5 bài LiveCommerce đạt, gồm quota đồng thời, room proof, quote/release, snapshot sau ẩn sản phẩm và lịch voucher. Database QA đã dọn.
- Order suite có một test cũ đòi RestClientResponseException sau đổi Inventory sang Feign; đã cập nhật kiểm tra ResponseStatusException 503 và giữ test retry/idempotency. CheckoutIntegrationTest 13 bài: 12 đạt, một PostgreSQL tùy chọn bỏ qua; gồm guard tổng thanh toán thay đổi.
- Relay signaling đạt: identity, consent, room isolation, shop moderator, history, ACK/dedup, pin và kết thúc phiên.
- Hai browser context: customer gửi từ form, KOL trả lời, mất ACK giữ bản nháp và retry không trùng, WebRTC video/audio giả lập hoạt động.
- Browser shop/customer: đặt giá theo variant, tự ghim/cập nhật customer, chọn phân loại trong phòng, voucher sàn cạnh sản phẩm và tung voucher mới đạt.
- Gateway/Product thật trên cổng QA + PostgreSQL clone: offer ownership, cấp proof có chữ ký, quote/reserve giá live và retry reservation đã commit sau ẩn sản phẩm đạt. JWT/JWKS và User là fixture riêng; không ghi vào database thật.
- FE build đạt. Suite FE rộng: 133 đạt, 101 bỏ qua theo cấu hình, 7 failure cũ; đã tái hiện đúng 7 failure trên worktree develop sạch trước sửa. Các failure cũ thuộc login/manual-order fixture, legacy voucher dialog, marketplace prototype và source assertions sidebar/auth. Contract live checkout/PayOS, cart/voucher và các browser trên đạt riêng.

Không coi fixture JWT/User/Voucher và thiết bị media giả lập là bằng chứng thanh toán PayOS thật hoặc media qua hai mạng Internet khác nhau.
