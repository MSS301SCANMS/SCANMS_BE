package com.scanms.order.mapper;

import com.scanms.order.dto.response.DeliveryNotificationSource;
import com.scanms.order.dto.response.OrderNotificationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface OrderNotificationMapper {
    @Mapping(target = "id", expression = "java(\"delivery-\" + source.sellerOrderId())")
    @Mapping(target = "userId", source = "customerId")
    @Mapping(target = "title", constant = "Đơn hàng đã giao thành công")
    @Mapping(target = "message", expression = "java(message(source))")
    @Mapping(target = "type", constant = "ORDER_DELIVERED")
    @Mapping(target = "data", expression = "java(data(source))")
    @Mapping(target = "isRead", source = "read")
    @Mapping(target = "createdAt", expression = "java(source.deliveredAt().toInstant(java.time.ZoneOffset.UTC))")
    OrderNotificationResponse toResponse(DeliveryNotificationSource source);

    default String message(DeliveryNotificationSource source) {
        String orderId = source.orderId().replace("-", "");
        String code = orderId.substring(0, Math.min(12, orderId.length())).toUpperCase(Locale.ROOT);
        return "Đơn DH-" + code + " đã được giao. Vào Đơn mua để kiểm tra và xác nhận đã nhận hàng.";
    }

    default Map<String, Object> data(DeliveryNotificationSource source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", source.orderId());
        result.put("sellerOrderId", source.sellerOrderId());
        result.put("storeId", source.storeId());
        result.put("actionUrl", "/customer/orders?orderId=" + source.orderId() + "&sellerOrderId=" + source.sellerOrderId());
        return result;
    }
}
