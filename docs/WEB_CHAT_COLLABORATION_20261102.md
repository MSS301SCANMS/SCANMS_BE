# Chat và hợp tác trên web — 02/11/2026

## Phạm vi và kiến trúc

Triển khai cho trình duyệt desktop của SCANMS. Dùng lại Product, Promotion/Affiliate, Gateway và relay Socket.IO hiện có. Không thêm microservice, framework hay thư viện UI. API theo Controller → Service interface → Impl; request DTO có validation; Product inquiry dùng MapStruct; Product kiểm tra thẻ hợp tác bằng OpenFeign và JWT của người thao tác.

Khi rà source: chat shop đã có bảng/lịch sử API nhưng FE mặc định polling, danh sách bị gộp theo shop thay vì conversation; deal đã có bản ghi và snapshot hoa hồng nhưng chưa thương lượng hai chiều; có màn hình sample ở FE nhưng không có SampleRequest/API trong Spring BE. Do đó sample được bổ sung tại Promotion, dùng lại profile KOL, partnership, catalog và các đường dẫn quản lý mẫu của web.

## Chức năng

- Khách/KOL mở chat từ shop/sản phẩm. Mỗi cặp store + customer có một conversation, tạo đồng thời được khóa và giữ unique constraint. Shop thấy riêng từng khách/KOL. Ngữ cảnh sản phẩm được lấy từ catalog ở BE, không tin tên/giá do trình duyệt gửi.
- Tin nhắn lưu DB, có cursor `(createdAt,id)`, preview/unread và đánh dấu đọc. Relay `/chat` xác minh JWT và tài khoản Spring, kiểm tra quyền mỗi lần join/send/read; chỉ gửi sự kiện tới hai người tham gia sau khi REST write đã commit. Token hết hạn sẽ ngắt kết nối.
- FE có trạng thái đang gửi/đã gửi/thất bại và gửi lại cùng messageId. Mất ACK sau commit không tạo tin trùng. Khi reconnect/reload, FE đọc lại lịch sử thật; polling bù khi relay tạm mất kết nối. Không kéo người đang đọc tin cũ xuống cuối. State/form/cache được tách theo tài khoản.
- KOL đề xuất tăng 1–3 điểm phần trăm theo quy tắc hiện có. Shop có thể counter; KOL chấp nhận/từ chối hoặc counter tiếp. `expectedVersion` bắt buộc cho thương lượng, khóa profile rồi deal, chặn tự chấp nhận và phiên bản cũ. Lịch sử nằm trên `ProductCommissionDeal`; không có bảng hoa hồng thứ hai. Deal chỉ có hiệu lực từ approvedAt, giữ cách snapshot theo placedAt của đơn cũ.
- KOL đã duyệt và có partnership ACCEPTED xin mẫu từ chat. Yêu cầu có UUID idempotency, sản phẩm/nội dung/người nhận/địa chỉ riêng tư. Một KOL không có hai yêu cầu còn hiệu lực cho cùng sản phẩm. Shop duyệt/từ chối có phản hồi; shop nhập đơn vị + mã vận đơn đã tạo để ghi nhận SHIPPED; KOL xác nhận RECEIVED. Sample không tự duyệt deal, không tự trừ ví, không tự tạo nhãn GHN hoặc giả lập callback giao hàng. Đây là quản lý yêu cầu mẫu, không phải đơn checkout trả tiền hay cơ chế trừ tồn kho mẫu.
- Ghim live giữ một productId trong productRefs. Owner hoặc KOL đã nhận lời và còn partnership mới thao tác được. BE kiểm tra danh sách phiên, store, trạng thái bán và variant còn hàng; `pinRevision` tăng dưới row lock, request cũ trả conflict. Sản phẩm hết hàng/ngừng bán/bị gỡ hoặc live kết thúc được bỏ ghim khi đọc trạng thái tiếp theo. Relay phát revision sau lưu; FE bỏ phản hồi cũ. Giá live, variant picker và attribution hiện có được giữ lại.

## File/module chính

- Product: `StoreChatController`, `StoreChatServiceImpl`, `StoreChatCards`, `StoreChatMapper`, `ChatCollaborationClient`, `LivePinPolicy`, `LivestreamServiceImpl`, `LiveCommerceServiceImpl`.
- Promotion: `ProductCommissionDealServiceImpl`, `CommissionNegotiationRequest`, `CollaborationChatServiceImpl`, `SampleRequestServiceImpl`, entity/repository/DTO/controller sample.
- Gateway: route `/api/v1/sample-requests/**` tới Promotion.
- Relay: `livestream-realtime/store-chat.mjs` và xử lý revision/refresh quyền trong `server.mjs`.
- FE: `SpringStoreChatProvider`, `store-chat.service`, `StoreChatWorkspace`, `StoreChatPanel`, composer/resource card, sample management page; tích hợp lại ChatBoxPage/widget/navigation. Các màn hình legacy chỉ còn dùng khi cấu hình legacy backend.

## Migration và chạy

1. Nếu Product DB chưa có bảng chat, chạy migration đã có: `product-service/src/main/resources/db/manual/20261028_store_chat.sql`.
2. Với Promotion dùng `ddl-auto=validate`, chạy `promotion-affiliate-service/db/20261102-chat-collaboration.sql` trên đúng Promotion DB. Migration chỉ thêm hai cột deal và bảng/index sample, không sửa/xóa dữ liệu cũ. Với `update`, Hibernate có thể tạo schema khi service khởi động lại.
3. Restart Product, Promotion/Affiliate và Gateway để nạp API mới. Restart relay hiện có để có namespace `/chat`:

   ```powershell
   cd D:\MSS301\be\livestream-realtime
   npm.cmd start
   ```

4. FE Spring tự dùng `VITE_SOCKET_URL`, nếu không có thì dùng `VITE_LIVE_SOCKET_URL`, local mặc định cổng 8090. Nếu từng đặt `VITE_SOCKET_URL=disabled`, bỏ giá trị đó hoặc đổi thành URL relay. Origins relay phải chứa origin FE đang dùng. Production dùng HTTPS/WSS phù hợp; không đưa secret vào FE.
5. Reload FE. Không cần tạo service account mới cho chat. Các cuộc gọi kiểm tra quyền dùng JWT người thao tác.

## API chính

Các đường dẫn dưới `/api/v1`, qua Gateway:

| API | Mục đích |
| --- | --- |
| GET/POST `/chat/conversations` | Inbox / mở hoặc tái sử dụng conversation |
| GET `.../{id}/messages?take=50&cursor=...` | Trang lịch sử cũ hơn |
| POST `.../{id}/messages` | `{messageText,messageId}` |
| PATCH `.../{id}/read` | Đánh dấu tin bên kia đã đọc |
| GET `.../{id}/subscription` | Relay kiểm tra quyền và định tuyến hai bên |
| POST `/referrals/commission-deals/{id}/negotiate` | `{action:ACCEPT|REJECT|COUNTER,expectedVersion,extraPoints,note}` |
| GET `/referrals/chat/resources/{type}/{id}` | Trạng thái có quyền của deal/sample, dùng trong thẻ |
| POST `/sample-requests` | `{requestId,productId,note,recipientName,recipientPhone,shippingAddress}` |
| GET `/sample-requests/my` hoặc `/shop` | Quản lý yêu cầu thật |
| PATCH `/sample-requests/{id}/{approve|reject|cancel|ship|receive}` | `{expectedVersion,response,carrierName,trackingNumber}` theo vai trò/bước |
| POST `/livestreams/{id}/pin` | `{productId:null|string,expectedRevision}` |

## Test thủ công

Dùng ba profile trình duyệt riêng: customer, shop owner, KOL đã duyệt. Shop/KOL phải có hợp tác ACCEPTED; live cần KOL nhận lời và trạng thái LIVE.

1. Customer mở sản phẩm → Chat → gửi thẻ sản phẩm và văn bản. Shop mở **Tin nhắn khách hàng**, trả lời. Reload hai bên, kiểm tra unread và tin cũ. Ngắt mạng rồi gửi lại: cùng tin không xuất hiện hai lần.
2. Shop mở chat KOL trong mục **KOL đã hợp tác**. KOL bấm **Đề xuất hoa hồng**, chọn sản phẩm/+1–3 điểm/ghi chú. Shop counter +2; KOL chấp nhận. Mở tab cũ và thử chấp nhận mức cũ: phải báo đã thay đổi. Đơn trước thời điểm duyệt giữ tỷ lệ đã chốt.
3. KOL bấm **Xin hàng dùng thử**, điền thông tin. Shop duyệt/từ chối. Nếu duyệt, nhập vận đơn thật đã tạo rồi xác nhận gửi. KOL bấm **Đã nhận hàng mẫu** khi đã nhận thực tế. Xem cùng bản ghi ở **Hàng dùng thử** trong menu shop/KOL. Xin lại khi yêu cầu còn hiệu lực bị chặn.
4. Owner/KOL mở Host Studio: ghim A → B → bỏ ghim. Người xem thấy thay đổi; reload vẫn đúng. Thử hai host thao tác cùng revision: một thành công, bên còn lại cần refresh. Hết hàng/ngừng bán/gỡ sản phẩm/kết thúc live: thẻ không còn mua được. Mua trong live vẫn phải chọn variant, giữ sourceLivestreamId/referral theo luồng hiện có.
5. Tài khoản ngoài cuộc trò chuyện không được đọc/gửi/join; ngoài phiên không được ghim.

## Bằng chứng và giới hạn kiểm tra

Kết quả lần cuối:

- Build/package cả tám module Java: thành công; FE production build: thành công.
- Nhóm integration/regression Product và Promotion: 32 trường hợp, 31 qua, 1 test seed PostgreSQL tùy chọn bỏ qua. Bao gồm hai thao tác ghim cùng revision và hai thao tác chấp nhận cùng phiên bản deal chỉ có một bên thành công.
- Relay Node: 6/6 qua.
- Ba bài kiểm tra browser: chat/hợp tác/hàng mẫu, ghim/giá live/variant và camera/micro WebRTC đều qua.
- Lint FE exit 0; còn 85 warning của toàn repo, không có warning trong component/provider chat mới ở lần kiểm tra cuối. `git diff --check` qua và scan không phát hiện khóa local trong các file thay đổi.

Kết quả cụ thể được ghi trong FE `docs/validation/web-chat-20261102.md` và báo cáo bàn giao. Test BE chạy persistence H2 thật với User/catalog boundary được mock; browser dùng API/JWT fixture riêng và relay Socket.IO thật. Camera/micro kiểm tra bằng thiết bị media giả lập. Các kiểm tra này không thay thế kiểm thử bằng tài khoản thật trên môi trường triển khai của nhóm, vận đơn GHN thật hay đường truyền Internet thực tế.

Không chạy migration trên DB ứng dụng, không seed/reset dữ liệu, không đổi .env chứa khóa và không tự dừng các service IntelliJ của người dùng trong lần triển khai này.
