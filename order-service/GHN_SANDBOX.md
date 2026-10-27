# Tính phí GHN sandbox khi checkout

Khách không cần tài khoản GHN. Order Service gọi GHN sandbox bằng tài khoản thử nghiệm của hệ thống. Tích hợp này chỉ báo giá, chưa tạo vận đơn hoặc gọi giao hàng thật.

## Cấu hình

Điền trong `.env` ở gốc SCANMS_BE (không commit token):

```dotenv
CHECKOUT_SHIPPING_PROVIDER=GHN_SANDBOX
GHN_SANDBOX_TOKEN=
GHN_SANDBOX_SHOP_ID=
GHN_SANDBOX_STORE_ID=02c1461c-aead-4366-9116-f72915bac9ce
GHN_SANDBOX_ITEM_WEIGHT_GRAMS=300
GHN_SANDBOX_PARCEL_LENGTH_CM=25
GHN_SANDBOX_PARCEL_WIDTH_CM=20
GHN_SANDBOX_PARCEL_HEIGHT_CM=8
```

Token và Shop ID phải thuộc cùng tài khoản sandbox. Địa chỉ lấy hàng được đọc từ shop GHN. Store ID là gian hàng ứng dụng gắn với shop thử nghiệm (mặc định Sora Skin); gian hàng khác chưa được cấu hình sẽ báo lỗi rõ ràng. Khởi động lại Order Service và Promotion Affiliate Service sau khi cập nhật code/cấu hình.

Khối lượng thử nghiệm bằng số sản phẩm nhân `GHN_SANDBOX_ITEM_WEIGHT_GRAMS`; kích thước kiện lấy từ cấu hình trên. Hiện chỉ hỗ trợ dịch vụ hàng nhẹ, tổng khối lượng dưới 20kg. Đây chưa phải trọng lượng/kích thước thực của từng SKU.

## Kiểm tra trên giao diện

1. Mở sản phẩm Sora Skin, chọn phân loại và Mua ngay (hoặc mua từ giỏ).
2. Chọn đủ tỉnh/thành, quận/huyện, phường/xã, địa chỉ chi tiết. Đợi tính phí và kiểm tra dòng Phí vận chuyển cùng tổng thanh toán.
3. Đổi sang một tuyến khác; đợi báo giá mới. Các địa chỉ cùng tuyến hoặc cùng bậc trọng lượng có thể có cùng phí.
4. Tăng số lượng để kiểm tra khối lượng kiện; không mặc định mỗi sản phẩm thêm vào sẽ làm tăng cước.
5. Áp voucher vận chuyển, đối chiếu phí gốc, giảm phí và tổng tiền; đổi địa chỉ thì voucher được tính lại.
6. Đặt một đơn thử: phí được kiểm tra lại phía BE và lưu vào đơn. Gửi lại cùng yêu cầu không tạo đơn mới hoặc thay đổi phí đã chốt.
7. Thử cấu hình thiếu token hoặc địa chỉ GHN không hỗ trợ: phải hiện lỗi, không cho đặt đơn với cước giả 0đ.

Mã địa giới trên FE được đối chiếu bằng tên với danh mục GHN; không sử dụng trực tiếp mã từ nguồn tỉnh/thành làm mã GHN. Không tìm thấy hoặc tên không xác định duy nhất sẽ yêu cầu chọn lại địa chỉ. Cước được cache 120 giây cho cùng tuyến/kiện.

Phí vận chuyển tách riêng tiền hàng tính hoa hồng. Các khoản giảm phí không giảm nhầm tiền hàng của KOL. Voucher nhận phí từng shop từ Order Service qua API nội bộ, không tin giá do trình duyệt gửi.

Tài liệu: https://developer.ghn.vn/vi/docs/order/calculate-fee
