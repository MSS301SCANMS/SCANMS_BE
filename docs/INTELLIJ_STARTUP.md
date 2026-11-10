# Chạy backend bằng IntelliJ trên Windows

Sau khi cài cấu hình trên máy này, mở Services, chọn nhóm **Spring Boot**, nhấn **Run**. Cả sáu cấu hình có bước **Before launch → Prepare local services** trước Build/Run Java.

Bước chuẩn bị chạy `scripts/Prepare-IntelliJ.ps1` để:

- Mở Docker Desktop nếu engine chưa chạy; bật các container database, Keycloak, Redis, Kafka hiện có. Không xóa hoặc tạo lại dữ liệu của container đã có.
- Chờ database, Redis và endpoint realm Keycloak sẵn sàng trước khi Java chạy.
- Đọc issuer qua endpoint Keycloak local, lấy client credential đang dùng từ Keycloak và tạo lại sáu file `.local/intellij/*.env`.
- Khôi phục resource cấu hình thiếu trong `target/classes`, tránh Gateway chạy nhưng API 404.
- Tự khởi động relay Node chat/livestream cổng 8090, dùng cùng metrics secret với Product service và lấy signing keys từ Keycloak local. Issuer vẫn được kiểm tra chính xác; không tắt xác thực JWT. Relay khỏe được dùng lại, không tạo nhiều bản trùng cổng. Nếu issuer hoặc cấu hình thay đổi, chỉ bản relay do script quản lý được khởi động lại.
- Khóa bước chuẩn bị để nhiều cấu hình Run cùng lúc không sửa môi trường hoặc khởi động container đồng thời.

Để dừng, chọn nhóm Spring Boot rồi Stop; chờ các tiến trình dừng xong trước khi Run lại. Docker/DB không bị dừng theo Stop Java. Không chạy thêm JAR nền cùng cổng 8080–8085.

Lần đầu khi Docker/Keycloak đang tắt sẽ lâu hơn. Tab **Prepare local services** cho biết đang chờ thành phần nào; khi xong Java tự chạy tiếp. Nếu hạ tầng không sẵn sàng trong thời gian chờ, bước chuẩn bị báo lỗi cụ thể và không cố chạy Java với DB chưa có.

## Cài lại hoặc dùng máy thành viên

Cần có Docker Desktop, IntelliJ với EnvFile plugin, JDK và Maven project đã import; file `.env` gốc và module phải được cấu hình trên từng máy. Không chia sẻ file chứa bí mật lên Git.

Thoát hẳn IntelliJ rồi chạy từ thư mục backend:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Prepare-IntelliJ.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-IntelliJ-Startup.ps1
```

Installer mặc định dành cho `IntelliJIdea2025.1`; nếu dùng phiên bản khác, truyền `-IdeaConfigDir` trỏ tới thư mục cấu hình tương ứng dưới `%APPDATA%/JetBrains`. Installer dùng sáu Run configuration đã có sẵn, lưu bản sao cấu hình trước khi sửa trong `.local/intellij` và thêm External Tool tên **SCANMS → Prepare local services**. Mở lại IntelliJ sau cài đặt.

Các script không yêu cầu tunnel public để khởi động service; đọc issuer bằng Keycloak local và dùng JWKS local. Nếu Keycloak được cấu hình phát token với issuer public, issuer đó được giữ để xác thực đúng token. Relay cũng được bật khi Run; cần Node 22 trở lên và cài dependencies trong `livestream-realtime` bằng `npm ci`.

## Demo public tự động trên máy hiện tại

Đã bật chế độ tùy chọn `.local/intellij/public-demo.json` với `enabled: true`. Mỗi lần Run trong IntelliJ, bước chuẩn bị gọi `Ensure-PublicDemo.ps1`: bật lại container web nếu bị Stop, tái dùng tiến trình tunnel đã theo dõi nếu còn chạy; nếu đã tắt thì tạo Quick Tunnel mới. Cần executable `.tools/cloudflared.exe` ở workspace và container `scanms-demo-web` đã được cấu hình.

Địa chỉ được ghi vào `SCANMS_FE/public/demo-public-config.json` và `dist/demo-public-config.json`; frontend kiểm tra cấu hình này mỗi lần bấm chia sẻ rồi kiểm tra `/live-health`. Nếu tunnel hoặc relay không truy cập được thì báo lỗi, không sao chép link chết. Bản FE public dùng API và socket cùng origin nên không cần build lại chỉ vì hostname tunnel đổi. Máy thành viên cần tự cài đặt web/tunnel trước khi bật chế độ này; các file cấu hình sinh ra không chứa thông tin truy cập tài khoản nhưng không được commit.

Stop Java trong IntelliJ không Stop tunnel hoặc relay. Nếu tắt máy/tunnel rồi tạo lại, URL Quick Tunnel có thể đổi; link cũ đã gửi không thể tự sửa hoặc chuyển hướng vì tên miền đó không thuộc nhóm. Hãy Run rồi sao chép và gửi lại link mới. Không thể bảo đảm uptime hay địa chỉ cố định bằng Quick Tunnel. Nếu mất Internet, bước chuẩn bị cảnh báo public offline nhưng vẫn cho các Spring service local khởi động.

Google cần Authorized JavaScript origins khớp origin mới mỗi khi tunnel đổi. Muốn đường dẫn Google và link share cố định, cần địa chỉ frontend ổn định và backend/relay có thể truy cập ổn định; chỉ đưa FE lên Vercel không tự di chuyển Java/Keycloak/database.

Relay chạy nền độc lập với nút Stop Java trong IntelliJ; không mất kết nối chat mỗi khi restart một service. Log ở `.local/intellij/relay.out.log`, `relay.err.log`; thông tin tiến trình trong `relay-process.json`. Cổng health: `http://127.0.0.1:8090/health`.

Không commit `.local/intellij/*.env`, metrics secret hoặc bản sao workspace. `.local/` đã có trong `.gitignore`.

## Xác minh ngày 07/11/2026

- Đã đóng/mở IntelliJ để nạp cấu hình mới.
- Đã Stop database, Keycloak, Redis và Kafka rồi Run toàn bộ trong IDE: Before launch tự bật hạ tầng; sáu service lên đủ cổng 8080–8085.
- Lần khởi động đó: health cả sáu service `UP`; đăng nhập customer và lấy profile qua Gateway thành công; API public categories HTTP 200.
- Đã Stop toàn bộ Java, xác nhận sáu cổng được giải phóng rồi Run lại toàn bộ: sáu service đều `UP`, đúng tiến trình IntelliJ, không có JAR nền giữ cổng. Customer, Shop và KOL đều đăng nhập/lấy profile được; API categories HTTP 200.
- Bằng chứng lần kiểm tra cuối: `.local/intellij/verification.json`, lúc 09:22 ngày 07/11/2026 (giờ Việt Nam).

Cơ chế dùng tính năng [Before launch/External Tools của IntelliJ](https://www.jetbrains.com/help/idea/configuring-third-party-tools.html).
