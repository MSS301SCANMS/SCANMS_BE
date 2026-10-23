package com.scanms.order.service.impl;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.constant.ShipmentStatus;
import com.scanms.order.entity.*;
import com.scanms.order.repository.*;
import com.scanms.order.service.CommerceAccess;
import com.scanms.order.service.CommerceLifecycle;
import com.scanms.order.service.RefundAllocation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Authenticated fulfillment and physical return commands. Money stays in Finance.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommerceLifecycleImpl implements CommerceLifecycle {
    private final EntityManager em;
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private final ShipmentRepository shipments;
    private final ReturnRequestRepository returns;
    private final CommerceAccess access;
    private final Environment environment;

    @Value("${scanms.shipping.simulation-enabled:false}")
    private boolean simulationEnabled;

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private <T> T find(Class<T> type, String id, boolean lock) {
        T row = em.find(type, id, lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (row == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return row;
    }

    private Order parent(SellerOrder seller) {
        return find(Order.class, seller.getOrderId(), false);
    }

    private void owner(Order order, Jwt jwt) {
        if (!Objects.equals(order.getCustomerId(), access.customer(jwt)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private void read(SellerOrder seller, Jwt jwt) {
        if (access.manager(jwt) || access.stores(jwt).contains(seller.getStoreId()))
            return;
        owner(parent(seller), jwt);
    }

    private void paid(Order order) {
        require(
                Set.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED).contains(order.getStatus()),
                "Order must be paid before fulfillment"
        );
    }

    private SellerOrder lockedSeller(String id) {
        SellerOrder row = find(SellerOrder.class, id, false);
        find(Order.class, row.getOrderId(), true);
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return row;
    }

    private void testMode(String environment) {
        if (!simulationAvailable() || !"TEST".equals(environment))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Local shipping simulation is disabled for this shipment"
            );
    }

    private boolean simulationAvailable() {
        return simulationEnabled && environment.acceptsProfiles(Profiles.of("local"))
                && !environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    @Override
    public SellerOrder confirm(String id, Jwt jwt) {
        SellerOrder row = lockedSeller(id);
        access.shop(jwt, row.getStoreId());
        paid(parent(row));
        if (Set.of(SellerOrderStatus.CONFIRMED, SellerOrderStatus.PROCESSING, SellerOrderStatus.SHIPPED, SellerOrderStatus.DELIVERED, SellerOrderStatus.COMPLETED).contains(row.getStatus()))
            return row;
        require(row.getStatus() == SellerOrderStatus.PENDING, "Order cannot be confirmed in its current status");
        row.setStatus(SellerOrderStatus.CONFIRMED);
        return sellers.save(row);
    }

    @Override
    public Map<String, Object> dispatch(String id, Dispatch input, Jwt jwt) {
        SellerOrder row = lockedSeller(id);
        access.shop(jwt, row.getStoreId());
        Order order = parent(row);
        paid(order);
        if (!Set.of("TEST", "MANUAL").contains(input.environment()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Live carrier booking is not configured; use TEST or an existing MANUAL waybill"
            );
        if ("TEST".equals(input.environment()))
            testMode("TEST");

        var previous = shipments.findBySellerOrderId(id);
        if (previous.isPresent()) {
            Shipment old = previous.get();
            require(input.environment().equals(old.getEnvironment())
                            && ("TEST".equals(input.environment())
                            || Objects.equals(old.getTrackingCode(), input.trackingCode())),
                    "This order already has a different shipment");
            return tracking(old);
        }

        require(Set.of(SellerOrderStatus.PENDING,
                        SellerOrderStatus.CONFIRMED,
                        SellerOrderStatus.PROCESSING).contains(row.getStatus()),
                "Order cannot be dispatched in its current status");

        // Dispatch is an atomic shop confirmation plus handover; an unpaid order never gets a waybill.
        if ("MANUAL".equals(input.environment()) && (input.carrier() == null || input.carrier().isBlank() || input.trackingCode() == null || input.trackingCode().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier and existing tracking code are required");
        String code = "TEST".equals(input.environment()) ? "SIM-" + UUID.randomUUID() : input.trackingCode().trim();
        Shipment shipment = shipments.saveAndFlush(Shipment.builder().sellerOrderId(id).environment(input.environment())
                .carrier("TEST".equals(input.environment()) ? "LOCAL_SIMULATION" : input.carrier().trim()).trackingCode(code).status(ShipmentStatus.SHIPPED).shippedAt(Instant.now()).build());
        row.setStatus(SellerOrderStatus.SHIPPED);
        var totals = new LinkedHashMap<>(row.getTotalsSnapshot() == null ? Map.of() : row.getTotalsSnapshot());
        totals.put("trackingCode", code);
        totals.put("carrier", shipment.getCarrier());
        totals.put("shippingEnvironment", input.environment());
        row.setTotalsSnapshot(totals);
        sellers.save(row);
        if (order.getStatus() == OrderStatus.PAID) {
            order.setStatus(OrderStatus.PROCESSING);
            orders.save(order);
        }
        return tracking(shipment);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> track(String code, Jwt jwt) {
        Shipment shipment = shipments.findById(code).orElseGet(() -> shipments.findAll().stream().filter(s -> code.equals(s.getTrackingCode()) || code.equals(s.getSellerOrderId())).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        read(find(SellerOrder.class, shipment.getSellerOrderId(), false), jwt);
        return tracking(shipment);
    }

    @Override
    public Map<String, Object> simulate(String id, boolean complete, Jwt jwt) {
        SellerOrder row = lockedSeller(id);
        read(row, jwt);
        Shipment shipment = shipments.findBySellerOrderId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        testMode(shipment.getEnvironment());
        paid(parent(row));
        if (shipment.getStatus() == ShipmentStatus.DELIVERED)
            return tracking(shipment);
        require(row.getStatus() == SellerOrderStatus.SHIPPED && Set.of(ShipmentStatus.SHIPPED, ShipmentStatus.IN_TRANSIT).contains(shipment.getStatus()), "Shipment cannot be simulated in its current status");
        shipment.setStatus(complete ? ShipmentStatus.DELIVERED : ShipmentStatus.IN_TRANSIT);
        if (complete)
            deliver(row, shipment);
        shipments.save(shipment);
        return tracking(shipment);
    }

    @Override
    public Map<String, Object> delivered(String id, Jwt jwt) {
        SellerOrder row = lockedSeller(id);
        access.shop(jwt, row.getStoreId());
        paid(parent(row));
        Shipment shipment = shipments.findBySellerOrderId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Dispatch first"));
        require(!"TEST".equals(shipment.getEnvironment()), "Use the simulation controls for a test shipment");
        if (shipment.getStatus() != ShipmentStatus.DELIVERED) {
            require(row.getStatus() == SellerOrderStatus.SHIPPED, "Order has not been dispatched");
            deliver(row, shipment);
            shipments.save(shipment);
        }
        return tracking(shipment);
    }

    private void deliver(SellerOrder row, Shipment shipment) {
        shipment.setStatus(ShipmentStatus.DELIVERED);
        if (shipment.getDeliveredAt() == null)
            shipment.setDeliveredAt(Instant.now());
        if (row.getDeliveredAt() == null) {
            row.setDeliveredAt(LocalDateTime.ofInstant(shipment.getDeliveredAt(), ZoneOffset.UTC));
            row.setReturnDeadline(row.getDeliveredAt().plusDays(14));
        }
        if (row.getStatus() != SellerOrderStatus.COMPLETED)
            row.setStatus(SellerOrderStatus.DELIVERED);
        sellers.save(row);
    }

    private Map<String, Object> tracking(Shipment s) {
        boolean test = "TEST".equals(s.getEnvironment());
        String status = s.getStatus().name().toLowerCase(Locale.ROOT);
        var result = new LinkedHashMap<String, Object>();
        result.put("shipmentId", s.getShipmentId());
        result.put("sellerOrderId", s.getSellerOrderId());
        result.put("orderCode", s.getTrackingCode());
        result.put("carrier", s.getCarrier());
        result.put("status", status);
        result.put("statusText", s.getStatus().name());
        result.put("environment", test ? "TEST" : "LIVE");
        result.put("simulationAvailable", test && simulationAvailable() && s.getStatus() != ShipmentStatus.DELIVERED);
        result.put("isSimulation", test && s.getStatus() == ShipmentStatus.IN_TRANSIT);
        result.put("timeline", List.of(Map.of("status", status, "title", s.getStatus().name(), "completed", true, "time", Objects.toString(s.getDeliveredAt() != null ? s.getDeliveredAt() : s.getShippedAt(), ""))));
        return result;
    }

    @Override
    public Map<String, Object> receipt(String id, Jwt jwt) {
        Order order = find(Order.class, id, true);
        owner(order, jwt);
        paid(order);
        var rows = sellers.findByOrderId(id);
        require(!rows.isEmpty() && rows.stream().allMatch(s -> Set.of(SellerOrderStatus.DELIVERED, SellerOrderStatus.COMPLETED).contains(s.getStatus())), "All parcels must be delivered before confirming receipt");
        for (var row : rows) {
            row.setStatus(SellerOrderStatus.COMPLETED);
            sellers.save(row);
        }
        order.setStatus(OrderStatus.COMPLETED);
        orders.save(order);
        return Map.of("orderId", id, "status", "COMPLETED");
    }

    @Override
    public Map<String, Object> createReturn(ReturnInput input, Jwt jwt) {
        OrderItem item = find(OrderItem.class, input.orderItemId(), true);
        SellerOrder seller = find(SellerOrder.class, item.getSellerOrderId(), false);
        Order order = parent(seller);
        owner(order, jwt);
        paid(order);
        var existing = returns.findByOrderItemId(item.getOrderItemId());
        Map<String, Object> evidence = input.evidenceRefs() == null ? Map.of() : input.evidenceRefs();
        for (var old : existing)
            if (input.idempotencyKey().equals(old.getRequestKey())) {
                require(Objects.equals(old.getQuantity(), input.quantity()) && Objects.equals(old.getReason(), input.reason()) && Objects.equals(old.getEvidenceRefs(), evidence), "Return key belongs to another request");
                return returnView(old);
            }
        require(Set.of(SellerOrderStatus.DELIVERED, SellerOrderStatus.COMPLETED).contains(seller.getStatus()) && seller.getDeliveredAt() != null, "Order has not been delivered");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        require(seller.getReturnDeadline() != null && !now.isAfter(seller.getReturnDeadline()), "The 14-day return window has expired");
        int reserved = 0;
        long reservedAmount = 0;
        for (var r : existing)
            if (!Set.of(ReturnRequestStatus.REJECTED, ReturnRequestStatus.CANCELLED).contains(r.getStatus())) {
                reserved = Math.addExact(reserved, r.getQuantity());
                reservedAmount = Math.addExact(reservedAmount, r.getRefundAmountVnd());
            }
        long amount;
        try {
            amount = RefundAllocation.allocate(item.getNetPaidAmountVnd(), item.getQuantity(), input.quantity(), reserved, reservedAmount);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Return quantity exceeds the remaining purchased quantity");
        }
        require(amount > 0, "This item has no refundable paid value");
        validateEvidence(evidence);
        ReturnRequest row = returns.saveAndFlush(ReturnRequest.builder().orderItemId(item.getOrderItemId()).quantity(input.quantity()).requestKey(input.idempotencyKey())
                .reason(input.reason()).evidenceRefs(new LinkedHashMap<>(evidence)).refundAmountVnd(amount).status(ReturnRequestStatus.REQUESTED).requestedAt(now).build());
        return returnView(row);
    }

    private void validateEvidence(Map<String, Object> evidence) {
        if (!(evidence.get("images") instanceof List<?> images) || images.isEmpty() || images.size() > 5)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide 1 to 5 evidence images");
        for (Object image : images)
            url(image);
        url(evidence.get("unboxingVideoUrl"));
        if (Objects.toString(evidence.get("details"), "").length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Details exceed 2000 characters");
    }

    private void url(Object value) {
        try {
            String text = (String) value;
            URI uri = URI.create(text);
            if (text.length() > 2048 || !Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence must have a valid HTTP(S) URL");
        }
    }

    private ReturnRequest lockedReturn(String id) {
        ReturnRequest row = find(ReturnRequest.class, id, false);
        find(OrderItem.class, row.getOrderItemId(), true);
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        return row;
    }

    private SellerOrder seller(ReturnRequest row) {
        return find(SellerOrder.class, find(OrderItem.class, row.getOrderItemId(), false).getSellerOrderId(), false);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getReturn(String id, Jwt jwt) {
        ReturnRequest row = find(ReturnRequest.class, id, false);
        read(seller(row), jwt);
        return returnView(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myReturns(Jwt jwt) {
        String customer = access.customer(jwt);
        return returns.findAll().stream().filter(r -> customer.equals(parent(seller(r)).getCustomerId())).map(this::returnView).toList();
    }

    @Override
    public Map<String, Object> cancelReturn(String id, Jwt jwt) {
        ReturnRequest row = lockedReturn(id);
        owner(parent(seller(row)), jwt);
        if (row.getStatus() != ReturnRequestStatus.CANCELLED) {
            require(row.getStatus() == ReturnRequestStatus.REQUESTED, "Only an undecided return can be cancelled");
            row.setStatus(ReturnRequestStatus.CANCELLED);
            row.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC));
            returns.save(row);
        }
        return returnView(row);
    }

    @Override
    public Map<String, Object> sendReturn(String id, Dispatch input, Jwt jwt) {
        ReturnRequest row = lockedReturn(id);
        owner(parent(seller(row)), jwt);
        if (!Set.of("TEST", "MANUAL").contains(input.environment()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose TEST or MANUAL return transport");
        if ("TEST".equals(input.environment()))
            testMode("TEST");
        if (row.getLogistics() != null && !row.getLogistics().isEmpty()) {
            require(input.environment().equals(row.getLogistics().get("environment")) && ("TEST".equals(input.environment()) || Objects.equals(input.trackingCode(), row.getLogistics().get("trackingCode"))), "Return already has another shipment");
            return returnView(row);
        }
        require(row.getStatus() == ReturnRequestStatus.APPROVED, "The manager must approve the return before sending goods");
        if ("MANUAL".equals(input.environment()) && (input.carrier() == null || input.carrier().isBlank() || input.trackingCode() == null || input.trackingCode().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Carrier and tracking code are required");
        row.setLogistics(Map.of("environment", input.environment(), "carrier", "TEST".equals(input.environment()) ? "LOCAL_SIMULATION" : input.carrier().trim(),
                "trackingCode", "TEST".equals(input.environment()) ? "SIM-RETURN-" + row.getReturnRequestId() : input.trackingCode().trim(), "bookedAt", Instant.now().toString(), "status", "BOOKED"));
        row.setStatus(ReturnRequestStatus.RETURNING);
        returns.save(row);
        return returnView(row);
    }

    @Override
    public Map<String, Object> simulateReturn(String id, String event, Jwt jwt) {
        ReturnRequest row = lockedReturn(id);
        read(seller(row), jwt);
        var logistics = new LinkedHashMap<>(row.getLogistics() == null ? Map.of() : row.getLogistics());
        testMode(Objects.toString(logistics.get("environment"), ""));
        if (!Set.of("picked", "delivered").contains(event))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported simulation event");
        require(row.getStatus() == ReturnRequestStatus.RETURNING, "Return shipment is not active");
        if (logistics.containsKey("deliveredAt") || (event.equals("picked") && logistics.containsKey("pickedUpAt")))
            return returnView(row);
        if (event.equals("delivered"))
            require(logistics.containsKey("pickedUpAt"), "Simulate pickup before delivery to the shop");
        logistics.put(event.equals("picked") ? "pickedUpAt" : "deliveredAt", Instant.now().toString());
        logistics.put("status", event.equals("picked") ? "PICKED" : "DELIVERED");
        row.setLogistics(logistics);
        returns.save(row);
        return returnView(row);
    }

    @Override
    public Map<String, Object> receiveReturn(String id, Jwt jwt) {
        ReturnRequest row = lockedReturn(id);
        access.shop(jwt, seller(row).getStoreId());
        if (Set.of(ReturnRequestStatus.RECEIVED, ReturnRequestStatus.REFUND_PENDING, ReturnRequestStatus.REFUNDED).contains(row.getStatus()))
            return returnView(row);
        require(row.getStatus() == ReturnRequestStatus.RETURNING, "Return has not been shipped to the shop");
        var logistics = new LinkedHashMap<>(row.getLogistics() == null ? Map.of() : row.getLogistics());
        if ("TEST".equals(logistics.get("environment")))
            require(logistics.containsKey("deliveredAt"), "Simulated return parcel has not reached the shop");
        logistics.put("receivedAt", Instant.now().toString());
        row.setLogistics(logistics);
        row.setStatus(ReturnRequestStatus.RECEIVED);
        returns.save(row);
        // FinanceDispatch credits the CUSTOMER wallet and acknowledges REFUNDED asynchronously.
        return returnView(row);
    }

    private Map<String, Object> returnView(ReturnRequest row) {
        OrderItem item = find(OrderItem.class, row.getOrderItemId(), false);
        SellerOrder seller = seller(row);
        Order order = parent(seller);
        var result = new LinkedHashMap<String, Object>();
        result.put("returnRequestId", row.getReturnRequestId());
        result.put("orderId", order.getOrderId());
        result.put("sellerOrderId", seller.getSellerOrderId());
        result.put("orderItemId", item.getOrderItemId());
        result.put("storeName", seller.getTotalsSnapshot() == null ? "" : seller.getTotalsSnapshot().get("storeName"));
        result.put("productSnapshot", item.getProductSnapshot());
        result.put("quantity", row.getQuantity());
        result.put("unitPrice", item.getUnitPrice());
        result.put("reason", row.getReason());
        result.put("evidenceRefs", row.getEvidenceRefs());
        result.put("status", row.getStatus());
        result.put("requestedAt", row.getRequestedAt());
        result.put("decisionReason", row.getDecisionReason());
        result.put("shopResponse", row.getShopResponse());
        result.put("refundAmountVnd", row.getRefundAmountVnd());
        result.put("refundReference", row.getRefundReference());
        result.put("resolvedAt", row.getResolvedAt());
        result.put("logistics", row.getLogistics());
        result.put("simulationEnabled", simulationAvailable());
        result.put("returnDeadline", seller.getReturnDeadline());
        return result;
    }
}
