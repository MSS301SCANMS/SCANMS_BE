# Livestream camera / micro trên SCANMS

Server Socket.IO hỗ trợ WebRTC giữa một host và các người xem. Video/âm thanh truyền trực tiếp giữa trình duyệt; server trao đổi tín hiệu kết nối, chat, ghim sản phẩm, biểu cảm và số người đang xem. Không tạo tài khoản kỹ thuật hoặc tự bắt đầu phiên trong database.

## Chạy local

Cần Node.js 22+, Keycloak 8180, Gateway 8080 và các service Spring liên quan đang chạy.

```powershell
cd D:\MSS301\be\livestream-realtime
npm.cmd ci
npm.cmd start
```

Hoặc từ thư mục `be`, chạy `powershell -ExecutionPolicy Bypass -File scripts/Start-Livestream.ps1`. Script kiểm tra cổng trước khi bật Node ở nền, không tắt tiến trình khác. Log/PID lưu trong `.local/livestream-realtime`. Chỉ bật các service Spring chưa bật relay này; `/health` trên cổng 8090 xác nhận relay đang chạy.

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

Không bắt buộc nhập voucher để tạo phiên Spring. Chủ shop có tab **Giá live & Voucher** trong Host Studio để đặt giá từng phân loại, số lượng và thời hạn, hoặc tung/lên lịch voucher có sẵn và tạo voucher STORE/LIVESTREAM mới. Hồ sơ tranh chấp của giao diện legacy vẫn tách riêng.

Shop vào phòng với vai trò điều phối, nhận video/âm thanh từ KOL giống khách xem, đồng thời xem/gửi bình luận và ghim sản phẩm. Shop không chiếm camera KOL. Khách không thấy Host Studio. Giá vừa đặt được ghim và cập nhật cho người xem; khi mua, khách chọn size/màu ngay trong phòng.

Nếu mất kết nối media nhưng chat vẫn hoạt động, FE thử lại tối đa 3 lần, cách nhau 8 giây. Nút **Kết nối lại camera** yêu cầu tạo lại kết nối WebRTC qua relay, không chỉ gọi `socket.connect()` trên socket đang còn kết nối. Relay chỉ chấp nhận yêu cầu từ người nhận video trong đúng phòng, có giới hạn tần suất; người điều phối không được gửi offer giả làm host.

Chat có ACK xác nhận, giữ bản nháp nếu gửi lỗi và deduplicate theo client message ID khi thử lại. Khi vào phòng muộn hoặc kết nối lại, người dùng nhận tối đa 100 tin gần nhất của phòng đang còn trong bộ nhớ. Đây không phải lịch sử chat lưu vĩnh viễn; restart relay sẽ mất buffer chat.

## Phạm vi và kiểm tra quyền

- Xác minh JWT RS256 bằng JWKS của Keycloak, đúng issuer, subject và thời hạn. Các kiểm tra quyền nghiệp vụ gọi API bằng token của người đang kết nối.
- Chỉ chủ shop hoặc KOL đã được duyệt, có trong phiên và chấp nhận lời mời mới có quyền host. UI hiện dành nút camera cho KOL.
- Chỉ phát khi Spring trả trạng thái `LIVE`. Mỗi phòng một host kết nối, mặc định tối đa 20 kết nối nhận media (khách + shop, mỗi tab tiêu thụ một kết nối); đây là giới hạn cấu hình, chưa phải kết quả đo tải. `MAX_VIEWERS_PER_ROOM` phải là số nguyên dương.
- Biểu tượng mắt đếm tài khoản đang nhận video, gồm cả shop điều phối; chỉ loại tài khoản người phát. Cùng subject mở nhiều tab chỉ tính một lần. KOL phát + shop xem = 1 mắt; thêm khách = 2. Khi một tab rời nhưng tab khác vẫn còn, số người không giảm. Đây là số đang xem, khác tổng lượt vào, unique cả phiên và peak đã lưu ở Product. FE hiển thị `—` khi chưa kết nối relay.
- Khi nhiều người vào cùng lúc, relay giới hạn số lượt xác minh đang chạy (`MAX_CONCURRENT_JOINS`, mặc định 32), trả `ROOM_BUSY`/thời gian thử lại hoặc `ROOM_FULL`. Kiểm tra sức chứa và thêm thành viên thực hiện liền nhau sau bước xác minh, không có `await` giữa hai bước.
- Broadcast số người được gom theo cửa sổ 100 ms; peak gửi theo chu kỳ kiểm tra phòng. Gửi view tới Product chạy một hàng đợi single-flight, tối đa 4 request đồng thời, tối đa 64 sự kiện mỗi lượt flush. Các giới hạn này giảm dồn tải, không biến P2P thành hệ thống phát sóng quy mô lớn.
- Ngăn gửi tín hiệu giữa phòng khác nhau hoặc giả vai trò host qua payload. Chat lấy danh tính từ JWT. Token được làm mới cùng phiên đăng nhập FE; đổi tài khoản đóng kết nối cũ.
- Chat/ghim/biểu cảm và số người đang kết nối nằm trong bộ nhớ server, chưa lưu lịch sử hoặc ghi hình. Tổng lượt xem, unique và peak đã gửi thành công được lưu ở Product/PostgreSQL và giữ lại sau restart relay.

## Khi dùng ngoài máy local

Cần triển khai server qua HTTPS/WSS, cho phép đúng origin FE và dùng TURN với thông tin truy cập có thời hạn nếu các thiết bị nằm sau NAT hoặc mạng hạn chế. `VITE_LIVE_ICE_SERVERS` nhận JSON mảng `RTCIceServer` để cấu hình STUN/TURN. Không đặt khóa quản trị TURN lâu dài trong FE.

Đây là WebRTC P2P: host gửi một luồng cho mỗi người nhận. Cần giải pháp SFU/media server và kiểm thử tải trước khi phục vụ phiên đông người. Chưa xác nhận thiết bị điện thoại hoặc các mạng Internet khác nhau trong bài kiểm tra local. Xem [kế hoạch mở rộng](../docs/LIVESTREAM_CAPACITY_20261027.md).

## Tim đồng bộ trong phiên

Relay giữ tổng chung, chỉ cộng một lần cho mỗi sự kiện `❤️` được chấp nhận. Emoji khác chỉ phát hiệu ứng. FE gửi một sự kiện/click, nhận ACK và số tuyệt đối từ relay; không dùng tổng tim localStorage làm dữ liệu thật. Retry ACK dùng cùng clientReactionId; relay giữ tối đa 2000 ID gần nhất để chống cộng trùng. Người vào sau/reload nhận tổng trong join ACK. Các tổng cũ tự cộng ở localStorage không được nhập vào tổng chung.

Relay gom ghi tối đa theo cửa sổ 500 ms rồi PATCH `/livestreams/{id}/reactions/stats`, kèm JWT và chữ ký `LIKES\n{id}\n{likeCount}\n{timestamp}`. Product khóa hàng livestream và lưu max của tổng đã nhận trong `statistics.likeCount`, nên gửi lại/đến muộn không cộng trùng hoặc giảm tổng. DTO công khai chỉ trả likeCount, không lộ statistics hoặc các ID người xem. Không cần cột/migration mới; cần restart Product để nhận endpoint mới và dùng cùng LIVE_METRICS_SECRET hiện có.

Sau restart relay, tổng đã lưu được đọc lại từ Product. Trước khi Product được cập nhật, ghi lỗi sẽ được thử lại; nếu relay bị dừng cưỡng bức hoặc cả hai dịch vụ mất trước khi ghi thành công thì phần chưa ghi có thể mất. Đây là bộ đếm cho một relay; chạy nhiều relay cần counter/idempotency dùng chung như Redis trước khi nhân bản tiến trình.

## Kiểm thử

```powershell
npm.cmd test
```

FE có `merchant-live-spring.browser.test.mjs` và `livestream-camera-realtime.browser.test.mjs`. Bài thử camera dùng ba browser context KOL/shop/khách độc lập, JWT ký riêng, API Spring giả lập và thiết bị camera/micro tổng hợp của Chromium. Kiểm tra `framesDecoded` video và `bytesReceived` audio, shop nhận video không chiếm host, đếm tài khoản trùng, kết nối lại media, tắt/bật camera, chat và ngắt luồng khi kết thúc. Mặc định cần cổng 8090 trống; có thể dùng `LIVE_TEST_RELAY_PORT` và một Vite QA riêng với `VITE_LIVE_SOCKET_URL` tương ứng để không dừng relay đang dùng. Không truy cập thiết bị hoặc dữ liệu người dùng thật.

Tài liệu nền: [Socket.IO server](https://socket.io/docs/v4/server-initialization/), [JWT/JWKS](https://github.com/panva/jose).
