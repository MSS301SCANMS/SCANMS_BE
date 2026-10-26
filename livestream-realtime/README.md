# Livestream camera / micro trên SCANMS

Server Socket.IO hỗ trợ WebRTC giữa một host và các người xem. Video/âm thanh truyền trực tiếp giữa trình duyệt; server trao đổi tín hiệu kết nối, chat, ghim sản phẩm, biểu cảm và số người đang xem. Không tạo tài khoản kỹ thuật hoặc tự bắt đầu phiên trong database.

## Chạy local

Cần Node.js 22+, Keycloak 8180, Gateway 8080 và các service Spring liên quan đang chạy.

```powershell
cd D:\MSS301\be\livestream-realtime
npm.cmd ci
npm.cmd start
```

FE cấu hình trong `fe/.env.local` (file local, không commit):

```dotenv
VITE_LIVE_SOCKET_URL=http://localhost:8090
```

Restart Vite nếu chưa nhận cấu hình. URL này riêng cho livestream, không kích hoạt namespace chat cũ chưa có backend.

Có thể copy `.env.example` thành `.env` để thay cổng, gateway, issuer và origin. Mặc định bind `127.0.0.1:8090`; `/health` chỉ xác nhận tiến trình Node đang chạy.

## Lưu thống kê vào Product

Chạy migration `product-service/src/main/resources/db/manual/20261026_livestream_statistics.sql` trước khi khởi động Product với `ddl-auto=validate`. Migration chỉ thêm cột JSONB và có thể chạy lại.

Đặt cùng một `LIVE_METRICS_SECRET` ngẫu nhiên, dài ít nhất 32 ký tự, trong `product-service/.env` và `livestream-realtime/.env`; khởi động lại hai tiến trình sau khi đổi. Có thể tạo giá trị bằng `node -e "console.log(require('node:crypto').randomBytes(32).toString('base64url'))"`. Không commit giá trị thật hoặc đưa vào biến `VITE_*`.

Relay gửi sự kiện vào `/livestreams/{id}/views` và peak vào `/livestreams/{id}/stats` qua Gateway, kèm JWT và chữ ký HMAC có thời hạn. Product lưu lượt vào phòng theo visit ID, unique theo issuer/subject đã băm và peak concurrent. Gửi lại cùng visit ID không cộng trùng; người dùng kết nối lại tạo lượt mới nhưng không tăng unique. Khách chưa đăng nhập không được ghi view. Mất kết nối API thì relay thử lại trong khi token còn hiệu lực; hàng đợi này nằm trong bộ nhớ nên restart relay có thể mất sự kiện chưa gửi.

## Luồng sử dụng

1. Shop và KOL đã được quản lý duyệt. Shop mời hợp tác, KOL chấp nhận.
2. Shop vào **Livestream → Tạo phiên live mới**, chọn KOL, sản phẩm và lịch.
3. KOL vào danh sách livestream của mình, chấp nhận lời mời phiên.
4. Shop bấm **Bắt đầu phát sóng**. Link dùng đúng `livestreamId` do Spring trả về.
5. KOL mở phòng, bấm **Bật camera**, cho phép camera/micro. Khách đăng nhập mở cùng link trong trình duyệt khác.
6. Host có thể tắt/bật camera, bật/tắt micro, chat và đổi sản phẩm ghim. Shop kết thúc phiên tại danh sách hoặc chi tiết; server ngắt video sau lần kiểm tra trạng thái kế tiếp (tối đa khoảng 10 giây khi API phản hồi bình thường).

Không bắt buộc nhập voucher để tạo phiên Spring. Tính năng tung voucher và hồ sơ tranh chấp livestream của giao diện cũ chưa có API Spring tương ứng nên được ẩn; không báo đã kích hoạt voucher chỉ vì lưu metadata.

## Phạm vi và kiểm tra quyền

- Xác minh JWT RS256 bằng JWKS của Keycloak, đúng issuer, subject và thời hạn. Các kiểm tra quyền nghiệp vụ gọi API bằng token của người đang kết nối.
- Chỉ chủ shop hoặc KOL đã được duyệt, có trong phiên và chấp nhận lời mời mới có quyền host. UI hiện dành nút camera cho KOL.
- Chỉ phát khi Spring trả trạng thái `LIVE`. Mỗi phòng một host kết nối, mặc định tối đa 20 người xem; đây là giới hạn cấu hình, chưa phải kết quả đo tải.
- Ngăn gửi tín hiệu giữa phòng khác nhau hoặc giả vai trò host qua payload. Chat lấy danh tính từ JWT. Token được làm mới cùng phiên đăng nhập FE; đổi tài khoản đóng kết nối cũ.
- Chat/ghim/biểu cảm và số người đang kết nối nằm trong bộ nhớ server, chưa lưu lịch sử hoặc ghi hình. Tổng lượt xem, unique và peak đã gửi thành công được lưu ở Product/PostgreSQL và giữ lại sau restart relay.

## Khi dùng ngoài máy local

Cần triển khai server qua HTTPS/WSS, cho phép đúng origin FE và dùng TURN với thông tin truy cập có thời hạn nếu các thiết bị nằm sau NAT hoặc mạng hạn chế. `VITE_LIVE_ICE_SERVERS` nhận JSON mảng `RTCIceServer` để cấu hình STUN/TURN. Không đặt khóa quản trị TURN lâu dài trong FE.

Đây là WebRTC P2P: host gửi một luồng cho mỗi người xem. Cần giải pháp SFU/media server và kiểm thử tải trước khi phục vụ phiên đông người. Chưa xác nhận thiết bị điện thoại hoặc các mạng Internet khác nhau trong bài kiểm tra local.

## Kiểm thử

```powershell
npm.cmd test
```

FE có `merchant-live-spring.browser.test.mjs` và `livestream-camera-realtime.browser.test.mjs`. Bài thử camera cần cổng 8090 trống, dùng hai browser context độc lập, JWT ký riêng, API Spring giả lập và thiết bị camera/micro tổng hợp của Chromium. Kiểm tra `framesDecoded` video và `bytesReceived` audio, tắt/bật lại camera, chat và ngắt luồng khi kết thúc; không truy cập thiết bị hoặc dữ liệu người dùng thật.

Tài liệu nền: [Socket.IO server](https://socket.io/docs/v4/server-initialization/), [JWT/JWKS](https://github.com/panva/jose).
