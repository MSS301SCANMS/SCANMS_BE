package com.scanms.product.service.impl;

import com.scanms.product.client.LiveVoucherClient;
import com.scanms.product.constant.*;
import com.scanms.product.dto.request.*;
import com.scanms.product.dto.response.*;
import com.scanms.product.entity.Livestream;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.LiveCommerceMapper;
import com.scanms.product.repository.*;
import com.scanms.product.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class LiveCommerceServiceImpl implements LiveCommerceService, LiveOfferPricing {
    private final LivestreamRepository lives;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductAccess access;
    private final LiveCommerceMapper mapper;
    private final LiveVoucherClient vouchers;
    private final LivePurchaseAccess purchases;
    private final LivestreamSchedulePolicy schedule;
    private final LivestreamBroadcastPolicy broadcast;

    @Transactional(readOnly = true)
    public LiveCommerceResponse get(String id, boolean management) {
        var live = lives.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (management) access.requireApprovedStore(live.getStoreId());
        return response(live, management);
    }

    public LiveCommerceResponse createOffer(String id, CreateLiveOfferRequest request) {
        // Match checkout lock order: products, livestreams, then variants.
        var product = products.findLockedByProductId(request.productId()).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        var live = owned(id);
        var variant = variants.findById(request.variantId()).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!product.getStoreId().equals(live.getStoreId()) || !product.getProductId().equals(variant.getProductId()))
            throw new AppException(ErrorCode.FORBIDDEN);
        if (product.getStatus() != ProductStatus.ACTIVE || variant.getStatus() != ProductVariantStatus.ACTIVE || variant.getStockQuantity() == null
                || request.quantityLimit() > variant.getStockQuantity() || variant.getPriceVnd() == null || request.priceVnd() > variant.getPriceVnd())
            throw invalid("Giá hoặc số lượng ưu đãi không hợp lệ.");
        var ids = productIds(live);
        if (!ids.contains(request.productId())) throw invalid("Sản phẩm chưa được gắn vào phiên live.");
        var now = Instant.now();
        var start = request.startsAt() == null ? now : request.startsAt();
        if (start.isBefore(now.minusSeconds(5)) || request.endsAt() != null && (!request.endsAt().isAfter(start) || !request.endsAt().isAfter(now)))
            throw invalid("Thời gian ưu đãi không hợp lệ.");
        var offers = rows(live.getProductRefs(), "offers");
        if (offers.size() >= 500) throw invalid("Phiên đã đạt giới hạn thay đổi ưu đãi.");
        for (var offer : offers)
            if (request.variantId().equals(offer.get("variantId")) && "ACTIVE".equals(offer.get("status")))
                offer.put("status", "STOPPED");
        var offer = new LinkedHashMap<String, Object>();
        offer.put("id", UUID.randomUUID().toString());
        offer.put("productId", request.productId());
        offer.put("variantId", request.variantId());
        offer.put("priceVnd", request.priceVnd());
        offer.put("quantityLimit", request.quantityLimit());
        offer.put("reservedQuantity", 0L);
        offer.put("startsAt", start.toString());
        if (request.endsAt() != null) offer.put("endsAt", request.endsAt().toString());
        offer.put("status", "ACTIVE");
        offers.add(offer);
        var refs = copy(live.getProductRefs());
        refs.put("offers", offers);
        refs.put("pinnedProductId", request.productId());
        live.setProductRefs(refs);
        return response(live, true);
    }

    public LiveCommerceResponse stopOffer(String id, String offerId) {
        var live = owned(id);
        var offers = rows(live.getProductRefs(), "offers");
        var offer = offers.stream().filter(row -> offerId.equals(row.get("id"))).findFirst().orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        offer.put("status", "STOPPED");
        var refs = copy(live.getProductRefs());
        refs.put("offers", offers);
        live.setProductRefs(refs);
        return response(live, true);
    }

    public LiveCommerceResponse createVoucherDrop(String id, CreateLiveVoucherDropRequest request) {
        var live = owned(id);
        var now = Instant.now();
        var release = request.releaseAt() == null ? now : request.releaseAt();
        if (release.isBefore(now.minusSeconds(5)) || !request.expiresAt().isAfter(release) || !request.expiresAt().isAfter(now))
            throw invalid("Thời gian tung voucher không hợp lệ.");
        Map<String, Object> voucher = findVoucher(live, request.voucherId());
        var from = parseDate(voucher.get("validFrom"));
        var until = parseDate(voucher.get("validUntil"));
        if (from == null || until == null || release.isBefore(from) || request.expiresAt().isAfter(until))
            throw invalid("Thời gian tung phải nằm trong hạn voucher.");
        Object limit = voucher.get("remainingUses");
        if (limit == null && voucher.get("limits") instanceof Map<?, ?> limits) limit = limits.get("totalUsageLimit");
        if (limit instanceof Number count && request.quantity() > count.longValue())
            throw invalid("Số lượt vượt giới hạn voucher.");
        var drops = rows(live.getParticipantRefs(), "voucherDrops");
        if (drops.size() >= 100) throw invalid("Phiên đã đạt giới hạn đợt tung voucher.");
        var drop = new LinkedHashMap<String, Object>();
        drop.put("id", UUID.randomUUID().toString());
        drop.put("voucherId", request.voucherId());
        for (String key : List.of("code", "issuerType", "discountType", "value", "cap", "minimumSubtotal"))
            if (voucher.get(key) != null) drop.put(key, voucher.get(key));
        drop.put("benefitType", voucher.getOrDefault("benefitType", voucher.get("scope") instanceof Map<?, ?> scope ? Objects.toString(scope.get("benefitType"), "GOODS") : "GOODS"));
        drop.put("quantity", request.quantity());
        drop.put("releaseAt", release.toString());
        drop.put("expiresAt", request.expiresAt().toString());
        drop.put("status", "SCHEDULED");
        drops.add(drop);
        var refs = copy(live.getParticipantRefs());
        refs.put("voucherDrops", drops);
        live.setParticipantRefs(refs);
        return response(live, true);
    }

    public LiveCommerceResponse cancelVoucherDrop(String id, String dropId) {
        var live = owned(id);
        var drops = rows(live.getParticipantRefs(), "voucherDrops");
        var drop = drops.stream().filter(row -> dropId.equals(row.get("id"))).findFirst().orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        drop.put("status", "CANCELLED");
        var refs = copy(live.getParticipantRefs());
        refs.put("voucherDrops", drops);
        live.setParticipantRefs(refs);
        return response(live, true);
    }

    public Map<String, Object> apply(Livestream live, String variantId, int quantity, boolean reserve, String token, String subject) {
        var offers = rows(live.getProductRefs(), "offers");
        var match = offers.stream().filter(row -> variantId.equals(row.get("variantId")) && "ACTIVE".equals(offerStatus(live, row))).findFirst();
        if (match.isEmpty()) return Map.of();
        if (!broadcast.available(live, Instant.now()))
            throw new AppException(ErrorCode.CONFLICT, "Phiên đang gián đoạn camera. Vui lòng chờ KOL kết nối lại để mua với giá live.");
        purchases.verify(token, live.getLivestreamId(), subject);
        var offer = match.get();
        long reserved = number(offer, "reservedQuantity"), limit = number(offer, "quantityLimit");
        if (quantity > limit - reserved)
            throw new AppException(ErrorCode.CONFLICT, "Số lượng ưu đãi trong live không còn đủ.");
        if (reserve) {
            offer.put("reservedQuantity", Math.addExact(reserved, quantity));
            var refs = copy(live.getProductRefs());
            refs.put("offers", offers);
            live.setProductRefs(refs);
        }
        return Map.of("unitPriceVnd", number(offer, "priceVnd"), "sourceLiveOfferId", offer.get("id"));
    }

    public void release(Map<String, Object> line) {
        if (line.get("sourceLiveOfferId") == null || line.get("sourceLivestreamId") == null) return;
        var live = lives.lockById(line.get("sourceLivestreamId").toString()).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        var offers = rows(live.getProductRefs(), "offers");
        for (var offer : offers)
            if (line.get("sourceLiveOfferId").equals(offer.get("id")))
                offer.put("reservedQuantity", Math.max(0, number(offer, "reservedQuantity") - number(line, "quantity")));
        var refs = copy(live.getProductRefs());
        refs.put("offers", offers);
        live.setProductRefs(refs);
    }

    private LiveCommerceResponse response(Livestream live, boolean management) {
        var offers = rows(live.getProductRefs(), "offers").stream().map(row -> {
                    row.put("status", offerStatus(live, row));
                    return row;
                })
                .filter(row -> management || "ACTIVE".equals(row.get("status"))).map(mapper::toOffer).toList();
        var drops = rows(live.getParticipantRefs(), "voucherDrops").stream().map(row -> {
                    row.put("status", dropStatus(live, row));
                    return row;
                })
                .filter(row -> management || "PUBLISHED".equals(row.get("status"))).map(mapper::toVoucherDrop).toList();
        return new LiveCommerceResponse(offers, drops, Objects.toString(live.getProductRefs() == null ? null : live.getProductRefs().get("pinnedProductId"), null));
    }

    private Map<String, Object> findVoucher(Livestream live, String id) {
        try {
            var publicResponse = vouchers.platform(live.getStoreId());
            if (publicResponse != null && publicResponse.get("result") instanceof Collection<?> list)
                for (Object value : list)
                    if (value instanceof Map<?, ?> row && id.equals(row.get("voucherId"))) return stringMap(row);
            var privateResponse = vouchers.get("Bearer " + access.bearerToken(), id);
            if (privateResponse != null && privateResponse.get("result") instanceof Map<?, ?> row) {
                if (!live.getStoreId().equals(row.get("storeId")) || !"STORE".equals(row.get("issuerType")))
                    throw new AppException(ErrorCode.FORBIDDEN);
                if ("LIVESTREAM".equals(row.get("scopeType")) && !live.getLivestreamId().equals(row.get("livestreamId")))
                    throw invalid("Voucher thuộc phiên khác.");
                if ("PRODUCT".equals(row.get("scopeType")) && !productIds(live).contains(row.get("productId")))
                    throw invalid("Voucher không áp dụng cho sản phẩm của phiên.");
                return stringMap(row);
            }
        } catch (feign.FeignException ex) {
            throw new AppException(ex.status() == 403 ? ErrorCode.FORBIDDEN : ErrorCode.CONFLICT, "Chưa xác minh được voucher để tung.");
        }
        throw invalid("Không tìm thấy voucher phù hợp.");
    }

    private Livestream owned(String id) {
        var live = lives.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        access.requireApprovedStore(live.getStoreId());
        if (!Set.of(LivestreamStatus.SCHEDULED, LivestreamStatus.LIVE).contains(live.getStatus()) || schedule.hasElapsed(live))
            throw new AppException(ErrorCode.CONFLICT, "Phiên đã kết thúc.");
        return live;
    }

    private String offerStatus(Livestream live, Map<String, Object> row) {
        var now = Instant.now();
        if (!"ACTIVE".equals(row.get("status"))) return Objects.toString(row.get("status"), "STOPPED");
        if (live.getStatus() == LivestreamStatus.ENDED || live.getStatus() == LivestreamStatus.CANCELLED || schedule.hasElapsed(live))
            return "ENDED";
        if (row.get("endsAt") != null && !Instant.parse(row.get("endsAt").toString()).isAfter(now)) return "EXPIRED";
        if (number(row, "reservedQuantity") >= number(row, "quantityLimit")) return "SOLD_OUT";
        return live.getStatus() != LivestreamStatus.LIVE || Instant.parse(row.get("startsAt").toString()).isAfter(now) ? "SCHEDULED" : "ACTIVE";
    }

    private String dropStatus(Livestream live, Map<String, Object> row) {
        if ("CANCELLED".equals(row.get("status"))) return "CANCELLED";
        if (live.getStatus() == LivestreamStatus.ENDED || live.getStatus() == LivestreamStatus.CANCELLED || schedule.hasElapsed(live))
            return "ENDED";
        if (!Instant.parse(row.get("expiresAt").toString()).isAfter(Instant.now())) return "EXPIRED";
        return live.getStatus() == LivestreamStatus.LIVE && !Instant.parse(row.get("releaseAt").toString()).isAfter(Instant.now()) ? "PUBLISHED" : "SCHEDULED";
    }

    private List<?> productIds(Livestream live) {
        return live.getProductRefs() != null && live.getProductRefs().get("productIds") instanceof List<?> ids ? ids : List.of();
    }

    private Instant parseDate(Object value) {
        if (value == null) return null;
        try {
            return Instant.parse(value.toString());
        } catch (java.time.format.DateTimeParseException ex) {
            return LocalDateTime.parse(value.toString()).toInstant(ZoneOffset.UTC);
        }
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.getOrDefault(key, 0L)).longValue();
    }

    private LinkedHashMap<String, Object> copy(Map<String, Object> value) {
        return new LinkedHashMap<>(value == null ? Map.of() : value);
    }

    private Map<String, Object> stringMap(Map<?, ?> value) {
        var result = new LinkedHashMap<String, Object>();
        value.forEach((key, item) -> result.put(key.toString(), item));
        return result;
    }

    private List<Map<String, Object>> rows(Map<String, Object> refs, String key) {
        var result = new ArrayList<Map<String, Object>>();
        if (refs != null && refs.get(key) instanceof Collection<?> list)
            for (Object value : list) if (value instanceof Map<?, ?> row) result.add(stringMap(row));
        return result;
    }

    private AppException invalid(String message) {
        return new AppException(ErrorCode.INVALID_REQUEST, message);
    }
}
