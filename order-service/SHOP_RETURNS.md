# Giải trình đổi trả của gian hàng

Trang `/merchant/returns` và `/merchant/return-requests` dùng API Spring qua gateway 8080:

- `GET /api/v1/returns/shop`: trả các yêu cầu có OrderItem/SellerOrder thuộc cửa hàng được xác minh bởi Product Service `/stores/mine` với JWT hiện tại.
- `POST /api/v1/returns/{returnRequestId}/shop-response`: lưu `{response, imageUrls}`; cần role SHOP_OWNER/SHOP_MANAGER và quyền sở hữu gian hàng. Không dùng orderId vì một đơn có thể có nhiều yêu cầu trả hàng.

Giải trình được lưu trong cột JSONB `return_requests.shop_response`, tách khỏi `evidence_refs` do khách cung cấp. Trước khi triển khai với schema không dùng Hibernate `update`, áp dụng migration cộng thêm `db/return-shop-response-upgrade.sql`. Migration không thay đổi dữ liệu bằng chứng hiện có.

Chỉ yêu cầu REQUESTED chưa có giải trình mới được phản hồi. Retry cùng nội dung/ảnh trả kết quả cũ; nội dung khác sau khi gửi bị từ chối với 409. Khóa dòng bảo vệ ghi giải trình và quyết định đồng thời. Giới hạn 5.000 ký tự, tối đa 10 URL ảnh HTTP(S); không nhận URL có tài khoản/mật khẩu.

Phản hồi của shop không đổi trạng thái yêu cầu, không gọi Payment Service và không xác nhận hoàn tiền. Quyết định vẫn thuộc API `/returns/{id}/decision` với quyền hiện có. `APPROVED` và `REFUNDED` được hiển thị riêng. Mốc đổi trả lấy `SellerOrder.returnDeadline`, không tính từ ngày tạo đơn.

API trả lỗi khi không xác minh được quyền cửa hàng; frontend hiển thị lỗi và nút thử lại thay vì biến lỗi thành danh sách rỗng. Chỉ hiển thị trạng thái không có yêu cầu sau khi API đã tải thành công.
