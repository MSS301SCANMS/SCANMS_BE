package com.scanms.order.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Tích hợp tính cước vận chuyển môi trường Sandbox của Giao Hàng Nhanh (GHN).
 * Chỉ phục vụ mục đích báo giá (Quote-only), không tạo đơn thật trên hệ thống nhà vận chuyển.
 * Sử dụng bộ nhớ đệm (In-memory Cache) có TTL để tối ưu thời gian phản hồi.
 */
@Component
public class GhnSandboxShipping {

    @Value("${scanms.checkout.shipping-provider:FIXED}")
    private String provider;

    @Value("${scanms.shipping.ghn-sandbox.token:}")
    private String token;

    @Value("${scanms.shipping.ghn-sandbox.shop-id:}")
    private String configuredShopId;

    private long shopId;

    @Value("${scanms.shipping.ghn-sandbox.store-id:}")
    private String storeId;

    @Value("${scanms.shipping.ghn-sandbox.item-weight-grams:300}")
    private int itemWeight;

    @Value("${scanms.shipping.ghn-sandbox.length-cm:25}")
    private int length;

    @Value("${scanms.shipping.ghn-sandbox.width-cm:20}")
    private int width;

    @Value("${scanms.shipping.ghn-sandbox.height-cm:8}")
    private int height;

    private String baseUrl = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    private final RestClient http;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Instant until, Object value) {}

    public record ParcelQuote(long fee, Map<String, Object> snapshot) {}

    public GhnSandboxShipping() {
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        if (!Set.of("FIXED", "GHN_SANDBOX").contains(provider)) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Cấu hình nhà vận chuyển không hợp lệ.");
        }
        return "GHN_SANDBOX".equals(provider);
    }

    public Map<String, Object> mode() {
        return Map.of(
                "shippingMode", "GHN_SANDBOX",
                "shippingPayer", "CUSTOMER",
                "shippingSettlementResponsible", "PLATFORM",
                "shippingSettlementStatus", "SANDBOX",
                "shippingCarrier", "GHN",
                "shippingEnvironment", "TEST"
        );
    }

    public ParcelQuote quote(
            String applicationStore,
            CheckoutService.ShippingDestination destination,
            List<Map<String, Object>> lines
    ) {
        try {
            shopId = Long.parseLong(configuredShopId.trim());
        } catch (RuntimeException ex) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Điền GHN_SANDBOX_SHOP_ID hợp lệ trong .env của BE.");
        }

        if (token.isBlank() || shopId <= 0 || storeId.isBlank()) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình GHN sandbox. Điền GHN_SANDBOX_TOKEN và GHN_SANDBOX_SHOP_ID trong .env của BE.");
        }
        if (!storeId.equals(applicationStore)) {
            throw fail(HttpStatus.CONFLICT, "Gian hàng này chưa được liên kết với shop GHN sandbox.");
        }
        if (destination == null || blank(destination.province()) || blank(destination.district())
                || blank(destination.ward()) || blank(destination.detail()) || destination.detail().trim().length() < 5) {
            throw fail(HttpStatus.BAD_REQUEST, "Chọn đủ tỉnh/thành, quận/huyện, phường/xã và địa chỉ chi tiết để tính cước GHN.");
        }
        if (itemWeight <= 0 || itemWeight > 20000 || length <= 0 || width <= 0 || height <= 0) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Cấu hình cân nặng/kích thước kiện GHN không hợp lệ.");
        }

        Map<?, ?> province = match(list(cached("provinces", () -> call("/master-data/province", null))), "ProvinceName", destination.province(), "tỉnh/thành");
        int provinceId = integer(province.get("ProvinceID"));

        Map<?, ?> district = match(list(cached("district:" + provinceId, () -> call("/master-data/district", Map.of("province_id", provinceId)))), "DistrictName", destination.district(), "quận/huyện");
        int districtId = integer(district.get("DistrictID"));

        Map<?, ?> ward = match(list(cached("ward:" + districtId, () -> call("/master-data/ward?district_id=" + districtId, null))), "WardName", destination.ward(), "phường/xã");
        String wardCode = Objects.toString(ward.get("WardCode"), "");
        if (wardCode.isBlank()) {
            throw fail(HttpStatus.CONFLICT, "GHN chưa xác định được mã phường/xã.");
        }

        Map<?, ?> shop = findShop();
        int fromDistrict = integer(shop.get("district_id"));
        String fromWard = Objects.toString(shop.get("ward_code"), "");
        if (fromDistrict <= 0 || fromWard.isBlank()) {
            throw fail(HttpStatus.CONFLICT, "Shop GHN sandbox chưa có địa chỉ lấy hàng hợp lệ.");
        }

        long quantity = 0;
        long value = 0;
        for (var line : lines) {
            quantity = Math.addExact(quantity, integer(line.get("quantity")));
            value = Math.addExact(value, number(line.get("grossAmountVnd")));
        }

        long weight = Math.multiplyExact(quantity, itemWeight);
        if (weight <= 0 || weight >= 20000) {
            throw fail(HttpStatus.CONFLICT, "Luồng GHN sandbox hiện hỗ trợ kiện hàng nhẹ dưới 20kg. Vui lòng giảm số lượng.");
        }

        String quoteKey = "fee:" + shopId + ":" + fromDistrict + ":" + fromWard + ":" + districtId + ":" + wardCode
                + ":" + weight + ":" + value + ":" + length + ":" + width + ":" + height;
        Cached hit = cache.get(quoteKey);
        if (hit != null && hit.until().isAfter(Instant.now())) {
            return (ParcelQuote) hit.value();
        }

        var services = list(cached("service:" + fromDistrict + ":" + districtId, () ->
                call("/v2/shipping-order/available-services", Map.of("shop_id", shopId, "from_district", fromDistrict, "to_district", districtId))));
        if (services.stream().noneMatch(s -> Objects.equals(Objects.toString(s.get("service_type_id")), "2"))) {
            throw fail(HttpStatus.CONFLICT, "GHN sandbox chưa hỗ trợ dịch vụ hàng nhẹ cho tuyến này.");
        }

        var request = new LinkedHashMap<String, Object>();
        request.put("from_district_id", fromDistrict);
        request.put("from_ward_code", fromWard);
        request.put("to_district_id", districtId);
        request.put("to_ward_code", wardCode);
        request.put("service_type_id", 2);
        request.put("weight", weight);
        request.put("length", length);
        request.put("width", width);
        request.put("height", height);
        request.put("insurance_value", value);

        Object raw = call("/v2/shipping-order/fee", request);
        if (!(raw instanceof Map<?, ?> fee)) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "GHN sandbox chưa trả về bảng cước hợp lệ.");
        }

        long total = number(fee.get("total"));
        if (total <= 0 || total > 10000000) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "GHN sandbox trả về phí không hợp lệ. Vui lòng tính lại.");
        }

        var snapshot = new LinkedHashMap<>(mode());
        snapshot.put("shippingOriginalFeeVnd", total);
        snapshot.put("ghnShopId", shopId);
        snapshot.put("ghnServiceTypeId", 2);
        snapshot.put("fromDistrictId", fromDistrict);
        snapshot.put("fromWardCode", fromWard);
        snapshot.put("toDistrictId", districtId);
        snapshot.put("toWardCode", wardCode);
        snapshot.put("parcelWeightGrams", weight);
        snapshot.put("parcelLengthCm", length);
        snapshot.put("parcelWidthCm", width);
        snapshot.put("parcelHeightCm", height);
        snapshot.put("parcelWeightSource", "CONFIGURED_SANDBOX_PER_ITEM");
        snapshot.put("shippingQuotedAt", Instant.now().toString());

        var quote = new ParcelQuote(total, Map.copyOf(snapshot));
        if (cache.size() > 500) {
            cache.clear();
        }
        cache.put(quoteKey, new Cached(Instant.now().plusSeconds(120), quote));
        return quote;
    }

    private Map<?, ?> findShop() {
        Object raw = cached("shop:" + shopId, () -> {
            long offset = 0;
            for (int page = 0; page < 20; page++) {
                Object data = call("/v2/shop/all?offset=" + offset + "&limit=100", null);
                if (!(data instanceof Map<?, ?> shops)) {
                    break;
                }
                var rows = list(shops.get("shops"));
                for (var shop : rows) {
                    if (number(shop.get("_id")) == shopId && number(shop.get("status")) == 1) {
                        return shop;
                    }
                }
                long next = shops.get("last_offset") == null ? 0 : number(shops.get("last_offset"));
                if (rows.size() < 100 || next <= offset) {
                    break;
                }
                offset = next;
            }
            throw fail(HttpStatus.CONFLICT, "Không tìm thấy Shop ID hoạt động thuộc token GHN sandbox này.");
        });
        return (Map<?, ?>) raw;
    }

    private Object cached(String key, Supplier<Object> fetch) {
        var previous = cache.get(key);
        if (previous != null && previous.until().isAfter(Instant.now())) {
            return previous.value();
        }
        Object result = fetch.get();
        if (cache.size() > 500) {
            cache.clear();
        }
        cache.put(key, new Cached(Instant.now().plusSeconds(3600), result));
        return result;
    }

    private Object call(String path, Map<String, Object> body) {
        try {
            Map<?, ?> response;
            if (body == null) {
                response = http.get()
                        .uri(baseUrl + path)
                        .header("Token", token)
                        .header("ShopId", Long.toString(shopId))
                        .retrieve()
                        .body(Map.class);
            } else {
                response = http.post()
                        .uri(baseUrl + path)
                        .header("Token", token)
                        .header("ShopId", Long.toString(shopId))
                        .body(body)
                        .retrieve()
                        .body(Map.class);
            }
            if (response == null || !"200".equals(Objects.toString(response.get("code"))) || response.get("data") == null) {
                throw fail(HttpStatus.SERVICE_UNAVAILABLE, "GHN sandbox chưa trả về dữ liệu hợp lệ.");
            }
            return response.get("data");
        } catch (RestClientException ex) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Chưa lấy được cước GHN sandbox. Kiểm tra Token/Shop ID, địa chỉ và thử tính lại.");
        }
    }

    private List<Map<?, ?>> list(Object value) {
        if (!(value instanceof List<?> rows) || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?>))) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Danh mục GHN sandbox chưa hợp lệ.");
        }
        return rows.stream().<Map<?, ?>>map(row -> (Map<?, ?>) row).toList();
    }

    private Map<?, ?> match(List<Map<?, ?>> rows, String key, String name, String label) {
        String wanted = normalize(name);
        var found = rows.stream().filter(row -> {
            if (wanted.equals(normalize(Objects.toString(row.get(key), "")))) {
                return true;
            }
            if (row.get("NameExtension") instanceof Collection<?> aliases) {
                return aliases.stream().anyMatch(a -> wanted.equals(normalize(Objects.toString(a, ""))));
            }
            return false;
        }).toList();
        if (found.size() != 1) {
            throw fail(HttpStatus.CONFLICT, "Chưa đối chiếu được " + label + " với danh mục GHN. Vui lòng chọn lại địa chỉ; không tự dùng mã hành chính thay mã GHN.");
        }
        return found.getFirst();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("^(thanh pho|tinh|quan|huyen|thi xa|phuong|xa|thi tran|tp[. ]+)\\s*", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private int integer(Object value) {
        return Math.toIntExact(number(value));
    }

    private long number(Object value) {
        try {
            return new BigDecimal(Objects.toString(value, "")).longValueExact();
        } catch (RuntimeException ex) {
            throw fail(HttpStatus.SERVICE_UNAVAILABLE, "Dữ liệu số từ GHN hoặc kiện hàng không hợp lệ.");
        }
    }

    private ResponseStatusException fail(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
