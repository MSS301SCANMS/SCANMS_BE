# SCANMS backend (Spring Boot microservices)

Nguồn nghiệp vụ để đối chiếu trong workspace: `SCANMS_Team_Assignment.docx` do nhóm cung cấp và sơ đồ conceptual SCANMS. Mã hiện tại gồm `user-service`, `product-service`, `order-service`, `payment-service`, `promotion-affiliate-service`, `scanms-gateway`; `ai-service` chạy riêng. Mỗi service giữ database của mình. Trao đổi xuyên service dùng UUID, snapshot và API, không dùng quan hệ JPA xuyên database.

| Owner | Module chính | Phạm vi |
| --- | --- | --- |
| Nhã | User, Gateway, security | Keycloak/JWT, role, route và ownership |
| Thịnh | Product; voucher trong Promotion | Store/Application, Product/Category/Image/Variant, Feedback, Livestream, Voucher/Redemption |
| Thắng | Order | Checkout, SellerOrder, Shipment, DiscountAllocation, Return |
| Tuấn | Payment | Payment, Wallet/ledger, refund, settlement, withdrawal, fee |
| Nam | AI; affiliate trong Promotion | AI analyze, Collaborator, Referral, CommissionPolicy/Commission |

## Chạy local

Product Service tự nạp catalog mặc định gồm 24 sản phẩm, 96 biến thể và 69 ảnh khi database catalog hoàn toàn rỗng. Không ghi đè dữ liệu đã có. Xem [hướng dẫn seed trang chủ](product-service/HOMEPAGE_SEED.md); tắt bằng `CATALOG_SEED_ENABLED=false` khi cần.

Java 21 và Maven 3.9+ được dùng cho các module Java. Chạy PostgreSQL theo `docker-compose.yml` của từng service cần dùng. Compose ở thư mục gốc chỉ chứa PostgreSQL cho Keycloak và Keycloak cổng 8180; có thể chạy bằng `docker compose up -d` khi Docker Engine sẵn sàng. Gateway chạy bằng IntelliJ/Maven ở cổng 8080. Các service mặc định ở 8081–8085; AI ở 8000.

`user-service/.env` được nạp khi chạy từ thư mục service hoặc từ root. Nó chứa `USER_DB_NAME`, `USER_DB_PORT`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `CLIENT_SECRET`; không commit file này. `product-service/.env` cũng được nạp tương tự; Compose của Product dùng `PRODUCT_DB_NAME` (mặc định `product_db`) và `PRODUCT_DB_PORT` (mặc định 5437) như cấu hình Spring để tránh trùng PostgreSQL cục bộ trên cổng 5432. Biến môi trường thực có thể ghi đè `.env`.

Keycloak phải có realm `scanms`, public client `scanms-frontend` dùng Standard Flow + PKCE S256 với redirect `http://localhost:5173/auth/callback`, và confidential client `scanms-client` cho User Service. Role nghiệp vụ là `USER`, `SHOP_OWNER`, `KOL_CTV`, `MANAGER`, `ADMIN`. File `keycloak/keycloak-import-settiing.json` là dữ liệu tham khảo; cần thay placeholder secret, rà tài khoản mẫu và quyền service account trước khi import. Compose không tự import realm này. Không đưa `CLIENT_SECRET` vào frontend.

Checkout mới yêu cầu service token riêng cho Order (`FINANCE_TOKEN_URL`, `ORDER_SERVICE_CLIENT_ID`, `ORDER_SERVICE_CLIENT_SECRET`) có quyền `ORDER_INTERNAL` để giữ tồn kho và áp voucher. Payment/affiliate cũng có service credential riêng trong cấu hình module. Thanh toán PayOS và payout chỉ hoạt động khi provider credentials được cấu hình. Chỉ có `CLIENT_SECRET` cho User Service chưa đủ để chạy E2E checkout/tài chính.

## Catalog mặc định và voucher

Trang sản phẩm/checkout hỗ trợ đồng thời **1 voucher giảm tiền hàng + 1 voucher vận chuyển** qua `couponCode` và `shippingCouponCode`. Order tính lại giá/ship từ nguồn nội bộ, Affiliate giữ lượt cho cả hai mã trong cùng transaction; thanh toán, hủy và khôi phục xử lý cả hai lượt. API gợi ý voucher sàn công khai: `GET /api/v1/vouchers/public/platform`.

Với **Promotion DB đã có từ bản cũ**, chạy migration `promotion-affiliate-service/src/main/resources/db/manual/20261025_two_checkout_vouchers.sql` trước khi thử hai voucher. Migration giữ nguyên bản ghi, đổi unique một redemption/đơn thành unique `(order_id,voucher_id)`; Hibernate update không tự xóa unique cũ. Database mới được tạo bằng entity hiện tại đã dùng ràng buộc mới.

Catalog mới hoàn toàn trống tự nạp seed từ resource, không cần mật khẩu Cloudinary để tạo lại ảnh. Chi tiết ở `product-service/HOMEPAGE_SEED.md`. Nếu dùng chung PostgreSQL online, xem `scripts/PRODUCT_CLOUD.md`; file kết nối thật và backup nằm ngoài Git trong `.local`.

Đối chiếu frontend/contract và các route hiện đang được nối vào giao diện nằm ở `../SCANMS_FE/BACKEND_ALIGNMENT.md`. API mới có trong controller chưa đồng nghĩa đã được kiểm chứng với Keycloak, PostgreSQL và provider thực. Đặc biệt cần kiểm tra ownership cho Store/Product/Voucher, vòng đời return 14 ngày và tính hoa hồng/settlement trước nghiệm thu theo DOCX.
