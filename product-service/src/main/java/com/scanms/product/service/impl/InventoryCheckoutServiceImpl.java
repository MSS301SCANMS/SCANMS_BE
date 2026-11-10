package com.scanms.product.service.impl;

import com.scanms.product.client.OrderCancellationClient;
import com.scanms.product.constant.*;
import com.scanms.product.dto.request.InventoryReservationInput;
import com.scanms.product.dto.request.InventoryReservationLine;
import com.scanms.product.entity.*;
import com.scanms.product.service.InventoryCheckoutService;
import com.scanms.product.service.LiveOfferPricing;
import com.scanms.product.service.LivestreamSchedulePolicy;
import feign.FeignException;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryCheckoutServiceImpl implements InventoryCheckoutService {
    private final EntityManager em;
    private final OrderCancellationClient cancellations;
    private final LiveOfferPricing livePricing;
    private final LivestreamSchedulePolicy schedule;

    private void internal(Jwt jwt) {
        var roles = new HashSet<String>();
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> list) {
            list.forEach(r -> roles.add(r.toString()));
        }
        Object resources = jwt.getClaim("resource_access");
        if (resources instanceof Map<?, ?> map) {
            map.values().forEach(v -> {
                if (v instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> list) {
                    list.forEach(r -> roles.add(r.toString()));
                }
            });
        }
        if (!roles.contains("ORDER_INTERNAL")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @Override
    public Map<String, Object> reserve(
            String orderId, InventoryReservationInput input, Jwt jwt) {
        return reserve(orderId, input, jwt, false);
    }

    @Override
    public Map<String, Object> quote(InventoryReservationInput input, Jwt jwt) {
        return reserve("quote", input, jwt, true);
    }

    private Map<String, Object> reserve(String orderId, InventoryReservationInput input, Jwt jwt, boolean preview) {
        internal(jwt);
        // Lock product rows in a fixed order, also serializing attempts for the same order/stock.
        List<String> productIds = input.items().stream().map(InventoryReservationLine::productId).distinct().sorted().toList();
        Map<String, Product> products = new HashMap<>();
        for (String id : productIds) {
            Product p = em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (p == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is not available");
            }
            products.put(id, p);
        }
        List<InventoryReservationLine> requested = input.items().stream()
                .sorted(Comparator.comparing(InventoryReservationLine::productId).thenComparing(l -> Objects.toString(l.variantId(), "")))
                .toList();
        if (requested.stream()
                .map(l -> l.productId() + ":" + Objects.toString(l.variantId(), ""))
                .distinct()
                .count()
                != requested.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merge duplicate cart lines before checkout");
        }
        String hash = JsonMapper.builder().build().writeValueAsString(requested.stream().map(line -> {
            var fields = new LinkedHashMap<String, Object>();
            fields.put("productId", line.productId());
            fields.put("variantId", line.variantId());
            fields.put("quantity", line.quantity());
            fields.put("sourceLivestreamId", line.sourceLivestreamId());
            return fields;
        }).toList());
        CheckoutReservation existing =
                preview ? null : em.find(CheckoutReservation.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash()) || "RELEASED".equals(existing.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Reservation reference belongs to another or released checkout");
            }
            return Map.of("orderId", orderId, "lines", existing.getLines());
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        Map<String, Livestream> sources = new HashMap<>();
        for (String liveId : requested.stream().map(InventoryReservationLine::sourceLivestreamId).filter(Objects::nonNull).distinct().sorted().toList())
            sources.put(liveId, em.find(Livestream.class, liveId, LockModeType.PESSIMISTIC_WRITE));
        for (InventoryReservationLine line : requested) {
            Product p = products.get(line.productId());
            if (p.getStatus() != ProductStatus.ACTIVE)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is not available");
            Store store = em.find(Store.class, p.getStoreId());
            if (store == null || store.getApprovalStatus() != StoreStatus.APPROVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Store is not approved");
            }
            if (line.sourceLivestreamId() != null) {
                Livestream livestream = sources.get(line.sourceLivestreamId());
                if (livestream == null || livestream.getStatus() != LivestreamStatus.LIVE || schedule.hasElapsed(livestream)
                        || !p.getStoreId().equals(livestream.getStoreId())
                        || livestream.getProductRefs() == null
                        || !(livestream.getProductRefs().get("productIds") instanceof Collection<?> ids)
                        || !ids.contains(p.getProductId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Livestream attribution is invalid");
                }
            }
            ProductVariant variant;
            if (line.variantId() == null || line.variantId().isBlank()) {
                List<ProductVariant> choices = em.createQuery(
                                "select v from ProductVariant v where v.productId=:p and v.status=:s",
                                ProductVariant.class)
                        .setParameter("p", p.getProductId())
                        .setParameter("s", ProductVariantStatus.ACTIVE)
                        .getResultList();
                if (choices.size() != 1) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Select an available product variant");
                }
                variant = choices.getFirst();
                em.refresh(variant, LockModeType.PESSIMISTIC_WRITE);
            } else {
                variant = em.find(ProductVariant.class, line.variantId(), LockModeType.PESSIMISTIC_WRITE);
            }
            if (variant == null || !p.getProductId().equals(variant.getProductId())
                    || variant.getStatus() != ProductVariantStatus.ACTIVE
                    || variant.getPriceVnd() == null
                    || variant.getPriceVnd() <= 0
                    || variant.getStockQuantity() == null
                    || variant.getStockQuantity() < line.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Variant or stock is unavailable");
            }
            if (!preview) variant.setStockQuantity(variant.getStockQuantity() - line.quantity());
            var liveOffer = line.sourceLivestreamId() == null ? Map.<String, Object>of() : livePricing.apply(sources.get(line.sourceLivestreamId()), variant.getVariantId(), line.quantity(), !preview, line.livePurchaseToken(), input.buyerIdentitySubject());
            long unitPrice = liveOffer.isEmpty() ? variant.getPriceVnd() : ((Number) liveOffer.get("unitPriceVnd")).longValue();
            if (unitPrice > variant.getPriceVnd())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Live offer must be refreshed after the catalog price changed");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("productId", p.getProductId());
            snapshot.put("variantId", variant.getVariantId());
            snapshot.put("sku", variant.getSku());
            snapshot.put("quantity", line.quantity());
            snapshot.put("unitPriceVnd", unitPrice);
            snapshot.put("listUnitPriceVnd", variant.getPriceVnd());
            snapshot.put("listAmountVnd", Math.multiplyExact(variant.getPriceVnd(), line.quantity()));
            snapshot.put("directDiscountVnd", Math.multiplyExact(variant.getPriceVnd() - unitPrice, line.quantity()));
            snapshot.put("grossAmountVnd", Math.multiplyExact(unitPrice, line.quantity()));
            if (liveOffer.get("sourceLiveOfferId") != null)
                snapshot.put("sourceLiveOfferId", liveOffer.get("sourceLiveOfferId"));
            snapshot.put("storeId", p.getStoreId());
            snapshot.put("storeName", store.getName());
            snapshot.put("name", p.getName());
            var images = em.createQuery("select i.imageUrl from ProductImage i where i.productId=:product order by i.primaryImage desc, i.sortOrder asc, i.imageId asc", String.class)
                    .setParameter("product", p.getProductId()).setMaxResults(1).getResultList();
            if (!images.isEmpty()) snapshot.put("imageUrl", images.getFirst());
            snapshot.put("size", variant.getSize());
            snapshot.put("color", variant.getColor());
            if (line.sourceLivestreamId() != null) snapshot.put("sourceLivestreamId", line.sourceLivestreamId());
            lines.add(snapshot);
        }
        if (!preview) {
            CheckoutReservation reservation = new CheckoutReservation();
            reservation.setOrderId(orderId);
            reservation.setRequestHash(hash);
            reservation.setLines(lines);
            reservation.setStatus("RESERVED");
            reservation.setCreatedAt(Instant.now());
            em.persist(reservation);
            em.flush();
        }
        return Map.of("orderId", orderId, "lines", lines);
    }

    @Override
    public Map<String, Object> release(String orderId, Jwt jwt) {
        internal(jwt);
        CheckoutReservation r = em.find(CheckoutReservation.class, orderId);
        if (r != null) {
            for (String product : r.getLines().stream()
                    .map(l -> l.get("productId").toString())
                    .distinct()
                    .sorted()
                    .toList()) {
                em.find(Product.class, product, LockModeType.PESSIMISTIC_WRITE);
            }
            em.refresh(r, LockModeType.PESSIMISTIC_WRITE);
        }
        if (r == null || "RELEASED".equals(r.getStatus())) return Map.of("released", true);
        if (!"RESERVED".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Committed stock cannot be released");
        }
        for (Map<String, Object> line : r.getLines().stream()
                .sorted(Comparator.comparing(l -> Objects.toString(l.get("sourceLivestreamId"), ""))).toList())
            livePricing.release(line);
        for (Map<String, Object> line : r.getLines().stream()
                .sorted(Comparator.comparing(l -> l.get("variantId").toString()))
                .toList()) {
            ProductVariant v = em.find(ProductVariant.class, line.get("variantId").toString(), LockModeType.PESSIMISTIC_WRITE);
            v.setStockQuantity(Math.addExact(v.getStockQuantity(), ((Number) line.get("quantity")).intValue()));
        }
        r.setStatus("RELEASED");
        return Map.of("released", true);
    }

    @Override
    public Map<String, Object> cancel(String orderId, Jwt jwt) {
        internal(jwt);
        CheckoutReservation reservation = em.find(CheckoutReservation.class, orderId);
        if (reservation == null || "RELEASED".equals(reservation.getStatus()))
            return Map.of("cancelled", true);
        // Keep the product -> reservation lock order used by reserve/release.
        for (String id : reservation.getLines().stream().map(l -> l.get("productId").toString()).distinct().sorted().toList())
            em.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE);
        em.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        if ("RELEASED".equals(reservation.getStatus())) return Map.of("cancelled", true);
        if ("COMMITTED".equals(reservation.getStatus())) {
            try {
                var response = cancellations.eligibility("Bearer " + jwt.getTokenValue(), orderId);
                if (response == null || !(response.get("result") instanceof Map<?, ?> result) || !Boolean.TRUE.equals(result.get("eligible")))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Committed stock can only be restored before delivery cancellation");
            } catch (FeignException ex) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify cancellation");
            }
        } else if (!"RESERVED".equals(reservation.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unknown reservation state");
        for (var line : reservation.getLines().stream().sorted(Comparator.comparing(l -> Objects.toString(l.get("sourceLivestreamId"), ""))).toList())
            livePricing.release(line);
        for (var line : reservation.getLines().stream().sorted(Comparator.comparing(l -> l.get("variantId").toString())).toList()) {
            var variant = em.find(ProductVariant.class, line.get("variantId").toString(), LockModeType.PESSIMISTIC_WRITE);
            variant.setStockQuantity(Math.addExact(variant.getStockQuantity(), ((Number) line.get("quantity")).intValue()));
        }
        reservation.setStatus("RELEASED");
        return Map.of("cancelled", true);
    }

    @Override
    public Map<String, Object> commit(String orderId, Jwt jwt) {
        internal(jwt);
        CheckoutReservation r = em.find(CheckoutReservation.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null || "RELEASED".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock reservation missing");
        }
        r.setStatus("COMMITTED");
        return Map.of("committed", true);
    }
}
