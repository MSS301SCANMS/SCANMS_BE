package com.scanms.order.service.impl;

import com.scanms.order.client.ProductClient;
import com.scanms.order.constant.*;
import com.scanms.order.dto.request.CheckoutItemRequest;
import com.scanms.order.dto.request.CheckoutRequest;
import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.*;
import com.scanms.order.entity.*;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.OrderItemMapper;
import com.scanms.order.mapper.OrderMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.DiscountAllocationService;
import com.scanms.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {
    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final SellerOrderRepository sellerOrderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final DiscountAllocationService discountAllocationService;
    private final ProductClient productClient;
    private final OrderItemMapper orderItemMapper;

    public OrderResponse create(CreateOrderRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Checkout items cannot be empty");
        }

        // 1. Idempotency Check
        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existingOpt = repository.findByIdempotencyKey(idempotencyKey);
            if (existingOpt.isPresent()) {
                log.info("Idempotent checkout hit for key: {}", idempotencyKey);
                return buildCheckoutResponse(existingOpt.get());
            }
        } else {
            idempotencyKey = "IDEM_" + UUID.randomUUID().toString();
        }

        // 2. Group items by storeId
        Map<String, List<CheckoutItemRequest>> itemsByStore = new LinkedHashMap<>();
        for (CheckoutItemRequest itemReq : request.items()) {
            String storeId = itemReq.storeId();
            if (storeId == null || storeId.isBlank()) {
                try {
                    Map<String, Object> prodData = productClient.getProduct(itemReq.productId());
                    if (prodData != null && prodData.containsKey("data")) {
                        Object dataObj = prodData.get("data");
                        if (dataObj instanceof Map<?, ?> m && m.get("storeId") != null) {
                            storeId = m.get("storeId").toString();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch storeId for product: {}", itemReq.productId());
                }
            }
            if (storeId == null || storeId.isBlank()) {
                storeId = "STORE_DEFAULT";
            }
            itemsByStore.computeIfAbsent(storeId, k -> new ArrayList<>()).add(itemReq);
        }

        // 3. Create master Order
        Map<String, Object> shippingSnapshot = new HashMap<>();
        if (request.shippingSnapshot() != null) {
            shippingSnapshot.putAll(request.shippingSnapshot());
        }
        if (request.customerName() != null) shippingSnapshot.put("customerName", request.customerName());
        if (request.customerPhone() != null) shippingSnapshot.put("customerPhone", request.customerPhone());
        if (request.customerEmail() != null) shippingSnapshot.put("customerEmail", request.customerEmail());
        if (request.shippingAddress() != null) shippingSnapshot.put("shippingAddress", request.shippingAddress());
        if (request.paymentMethod() != null) shippingSnapshot.put("paymentMethod", request.paymentMethod());
        if (request.note() != null) shippingSnapshot.put("note", request.note());

        Order order = Order.builder()
                .customerId(request.customerId() != null && !request.customerId().isBlank() ? request.customerId() : "GUEST")
                .idempotencyKey(idempotencyKey)
                .currency(request.currency() != null && !request.currency().isBlank() ? request.currency() : "VND")
                .status(OrderStatus.PENDING)
                .shippingSnapshot(shippingSnapshot)
                .payableVnd(0L)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        order = repository.save(order);

        // 4. Create SellerOrders and OrderItems
        List<SellerOrder> createdSellerOrders = new ArrayList<>();
        List<OrderItem> allOrderItems = new ArrayList<>();

        for (Map.Entry<String, List<CheckoutItemRequest>> entry : itemsByStore.entrySet()) {
            String storeId = entry.getKey();
            List<CheckoutItemRequest> storeItems = entry.getValue();

            SellerOrder sellerOrder = SellerOrder.builder()
                    .orderId(order.getOrderId())
                    .storeId(storeId)
                    .status(SellerOrderStatus.PENDING)
                    .totalsSnapshot(new HashMap<>())
                    .build();
            sellerOrder = sellerOrderRepository.save(sellerOrder);
            createdSellerOrders.add(sellerOrder);

            for (CheckoutItemRequest it : storeItems) {
                long itemGross = it.unitPrice() * it.quantity();

                Map<String, Object> prodSnapshot = new HashMap<>();
                if (it.productTitle() != null) prodSnapshot.put("title", it.productTitle());
                if (it.sku() != null) prodSnapshot.put("sku", it.sku());
                if (it.imageUrl() != null) prodSnapshot.put("imageUrl", it.imageUrl());
                if (it.selectedSize() != null) prodSnapshot.put("selectedSize", it.selectedSize());
                if (it.attributes() != null) prodSnapshot.putAll(it.attributes());

                OrderItem orderItem = OrderItem.builder()
                        .sellerOrderId(sellerOrder.getSellerOrderId())
                        .productId(it.productId())
                        .variantId(it.variantId())
                        .sourceLivestreamId(it.sourceLivestreamId())
                        .referralLinkId(it.referralLinkId())
                        .quantity(it.quantity())
                        .selectedSize(it.selectedSize())
                        .unitPrice(it.unitPrice())
                        .grossAmountVnd(itemGross)
                        .discountAmountVnd(0L)
                        .netPaidAmountVnd(itemGross)
                        .productSnapshot(prodSnapshot)
                        .build();
                orderItem = orderItemRepository.save(orderItem);
                allOrderItems.add(orderItem);
            }

            // Create initial shipment for seller order
            Shipment shipment = Shipment.builder()
                    .sellerOrderId(sellerOrder.getSellerOrderId())
                    .status(ShipmentStatus.PENDING)
                    .build();
            shipmentRepository.save(shipment);
        }

        // 5. Discount Allocation (Proportional Allocation with Penny Rounding)
        Long totalDiscountVnd = request.voucherDiscountVnd() != null ? Math.max(0, request.voucherDiscountVnd()) : 0L;
        if (totalDiscountVnd > 0) {
            Map<String, Long> itemAllocations = discountAllocationService.allocateOrderDiscount(allOrderItems, totalDiscountVnd);

            DiscountFundingType funding = DiscountFundingType.PLATFORM;
            if ("STORE".equalsIgnoreCase(request.voucherFundingSource())) {
                funding = DiscountFundingType.STORE;
            } else if ("SHARED".equalsIgnoreCase(request.voucherFundingSource())) {
                funding = DiscountFundingType.SHARED;
            }

            discountAllocationService.createAllocations(allOrderItems, request.voucherCode(), funding, itemAllocations);

            for (OrderItem oi : allOrderItems) {
                Long alloc = itemAllocations.getOrDefault(oi.getOrderItemId(), 0L);
                oi.setDiscountAmountVnd(alloc);
                oi.setNetPaidAmountVnd(Math.max(0, oi.getGrossAmountVnd() - alloc));
                orderItemRepository.save(oi);
            }
        }

        // 6. Update Totals for each SellerOrder and master Order
        long masterPayable = 0L;
        for (SellerOrder so : createdSellerOrders) {
            List<OrderItem> soItems = allOrderItems.stream()
                    .filter(oi -> oi.getSellerOrderId().equals(so.getSellerOrderId()))
                    .toList();
            long soGross = soItems.stream().mapToLong(OrderItem::getGrossAmountVnd).sum();
            long soDiscount = soItems.stream().mapToLong(OrderItem::getDiscountAmountVnd).sum();
            long soNet = soItems.stream().mapToLong(OrderItem::getNetPaidAmountVnd).sum();

            Map<String, Object> totals = new HashMap<>();
            totals.put("subtotalVnd", soGross);
            totals.put("discountVnd", soDiscount);
            totals.put("payableVnd", soNet);
            totals.put("itemCount", soItems.size());
            so.setTotalsSnapshot(totals);
            sellerOrderRepository.save(so);

            masterPayable += soNet;
        }

        order.setPayableVnd(masterPayable);
        repository.save(order);

        return buildCheckoutResponse(order, createdSellerOrders, allOrderItems);
    }

    @Override
    @Transactional
    public OrderResponse markPaid(String orderId, String transactionReference) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        order.setStatus(OrderStatus.PAID);
        Map<String, Object> saga = order.getSagaState() != null ? new HashMap<>(order.getSagaState()) : new HashMap<>();
        saga.put("paidAt", LocalDateTime.now().toString());
        if (transactionReference != null) {
            saga.put("transactionReference", transactionReference);
        }
        order.setSagaState(saga);
        repository.save(order);

        List<SellerOrder> sellerOrders = sellerOrderRepository.findByOrderId(orderId);
        for (SellerOrder so : sellerOrders) {
            if (so.getStatus() == SellerOrderStatus.PENDING) {
                so.setStatus(SellerOrderStatus.CONFIRMED);
                sellerOrderRepository.save(so);
            }
        }

        return mapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Collections.emptyList();
        }
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutResponse getCheckoutDetails(String orderId) {
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));
        return buildCheckoutResponse(order);
    }

    private CheckoutResponse buildCheckoutResponse(Order order) {
        return buildCheckoutResponse(order, null, null);
    }

    private CheckoutResponse buildCheckoutResponse(Order order, List<SellerOrder> existingSellerOrders, List<OrderItem> existingItems) {
        List<SellerOrder> sellerOrders = (existingSellerOrders != null && !existingSellerOrders.isEmpty())
                ? existingSellerOrders
                : sellerOrderRepository.findByOrderId(order.getOrderId());
        List<SellerOrderSummaryResponse> soResponses = new ArrayList<>();

        long masterGross = 0L;
        long masterDiscount = 0L;

        for (SellerOrder so : sellerOrders) {
            List<OrderItem> items;
            if (existingItems != null && !existingItems.isEmpty()) {
                items = existingItems.stream()
                        .filter(oi -> oi.getSellerOrderId().equals(so.getSellerOrderId()))
                        .toList();
            } else {
                items = orderItemRepository.findBySellerOrderId(so.getSellerOrderId());
            }
            List<OrderItemResponse> itemResponses = items.stream().map(orderItemMapper::toResponse).toList();

            long soGross = items.stream().mapToLong(OrderItem::getGrossAmountVnd).sum();
            long soDiscount = items.stream().mapToLong(OrderItem::getDiscountAmountVnd).sum();
            masterGross += soGross;
            masterDiscount += soDiscount;

            String shipmentId = shipmentRepository.findBySellerOrderId(so.getSellerOrderId())
                    .map(Shipment::getShipmentId)
                    .orElse(null);

            soResponses.add(new SellerOrderSummaryResponse(
                    so.getSellerOrderId(),
                    so.getOrderId(),
                    so.getStoreId(),
                    so.getStatus(),
                    so.getTotalsSnapshot(),
                    shipmentId,
                    so.getDeliveredAt(),
                    so.getReturnDeadline(),
                    itemResponses
            ));
        }

        return new CheckoutResponse(
                order.getOrderId(),
                order.getOrderId(), // publicOrderCode
                order.getCustomerId(),
                order.getIdempotencyKey(),
                order.getPayableVnd(),
                order.getPayableVnd(), // finalAmount
                masterGross,
                masterDiscount,
                order.getCurrency(),
                order.getStatus(),
                order.getShippingSnapshot(),
                order.getCreatedAt(),
                soResponses
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> trackOrders(String phone, String orderSn) {
        Set<Order> matchedOrders = new LinkedHashSet<>();

        if (orderSn != null && !orderSn.isBlank()) {
            String trimmedSn = orderSn.trim();
            if (trimmedSn.length() >= 6) {
                repository.findById(trimmedSn).ifPresent(matchedOrders::add);
                if (matchedOrders.isEmpty()) {
                    repository.findByIdempotencyKey(trimmedSn).ifPresent(matchedOrders::add);
                }
            }
        }

        if (phone != null && !phone.isBlank()) {
            String trimmedPhone = phone.trim();
            // Khớp chính xác số điện thoại (tối thiểu 9 ký tự, không cho phép nhập số đơn lẻ như '0')
            if (trimmedPhone.length() >= 9) {
                List<Order> allOrders = repository.findAllByOrderByCreatedAtDesc();
                for (Order o : allOrders) {
                    if (o.getShippingSnapshot() != null) {
                        Object p = o.getShippingSnapshot().get("customerPhone");
                        if (p != null && p.toString().trim().equalsIgnoreCase(trimmedPhone)) {
                            matchedOrders.add(o);
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> resultOrders = new ArrayList<>();
        for (Order o : matchedOrders) {
            Map<String, Object> orderMap = new HashMap<>();
            orderMap.put("id", o.getOrderId());
            orderMap.put("externalOrderSn", o.getOrderId());

            Map<String, Object> snap = o.getShippingSnapshot() != null ? o.getShippingSnapshot() : Collections.emptyMap();
            orderMap.put("customerName", snap.getOrDefault("customerName", "Khách hàng"));
            orderMap.put("customerPhone", snap.getOrDefault("customerPhone", ""));
            orderMap.put("shippingAddress", snap.getOrDefault("shippingAddress", ""));
            orderMap.put("paymentMethod", snap.getOrDefault("paymentMethod", "COD"));
            orderMap.put("finalAmount", o.getPayableVnd() != null ? o.getPayableVnd() : 0L);

            List<SellerOrder> sellerOrders = sellerOrderRepository.findByOrderId(o.getOrderId());
            long totalGross = 0;
            long totalDiscount = 0;
            List<Map<String, Object>> itemsList = new ArrayList<>();
            List<Map<String, Object>> sellerOrdersList = new ArrayList<>();
            LocalDateTime earliestDeadline = null;
            boolean anyDelivered = false;

            for (SellerOrder so : sellerOrders) {
                long subtotal = 0;
                long discount = 0;
                if (so.getTotalsSnapshot() != null) {
                    Object sub = so.getTotalsSnapshot().get("subtotalVnd");
                    if (sub instanceof Number n) subtotal = n.longValue();
                    Object disc = so.getTotalsSnapshot().get("discountVnd");
                    if (disc instanceof Number n) discount = n.longValue();
                }
                totalGross += subtotal;
                totalDiscount += discount;

                Map<String, Object> soMap = new HashMap<>();
                soMap.put("sellerOrderId", so.getSellerOrderId());
                soMap.put("storeId", so.getStoreId());
                soMap.put("status", so.getStatus().name());
                soMap.put("deliveredAt", so.getDeliveredAt());
                soMap.put("returnDeadline", so.getReturnDeadline());

                if (so.getReturnDeadline() != null) {
                    earliestDeadline = so.getReturnDeadline();
                }
                if (so.getStatus() == SellerOrderStatus.DELIVERED) {
                    anyDelivered = true;
                }

                Optional<Shipment> shipOpt = shipmentRepository.findBySellerOrderId(so.getSellerOrderId());
                if (shipOpt.isPresent()) {
                    Shipment s = shipOpt.get();
                    soMap.put("shipmentStatus", s.getStatus().name());
                    soMap.put("trackingNumber", s.getTrackingCode());
                    soMap.put("carrierName", s.getCarrier());
                    if (s.getStatus() == ShipmentStatus.DELIVERED) {
                        anyDelivered = true;
                    }
                }

                List<OrderItem> items = orderItemRepository.findBySellerOrderId(so.getSellerOrderId());
                for (OrderItem it : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", it.getOrderItemId());
                    itemMap.put("orderItemId", it.getOrderItemId());
                    itemMap.put("sellerOrderId", so.getSellerOrderId());
                    itemMap.put("storeId", so.getStoreId());
                    itemMap.put("productId", it.getProductId());

                    Map<String, Object> prodSnap = it.getProductSnapshot() != null ? it.getProductSnapshot() : Collections.emptyMap();
                    itemMap.put("productTitle", prodSnap.getOrDefault("title", "Sản phẩm " + it.getProductId()));
                    itemMap.put("sku", prodSnap.getOrDefault("sku", "SKU-" + it.getProductId()));
                    itemMap.put("imageUrl", prodSnap.getOrDefault("imageUrl", ""));
                    itemMap.put("quantity", it.getQuantity());
                    itemMap.put("unitPrice", it.getUnitPrice() != null ? it.getUnitPrice() : 0L);
                    itemMap.put("totalPrice", it.getGrossAmountVnd() != null ? it.getGrossAmountVnd() : 0L);
                    itemMap.put("allocatedDiscountVnd", it.getDiscountAmountVnd() != null ? it.getDiscountAmountVnd() : 0L);
                    itemMap.put("netPayableVnd", it.getNetPaidAmountVnd() != null ? it.getNetPaidAmountVnd() : 0L);

                    boolean isEligibleForReturn = (anyDelivered || so.getStatus() == SellerOrderStatus.DELIVERED)
                            && (earliestDeadline == null || earliestDeadline.isAfter(LocalDateTime.now()));
                    itemMap.put("returnEligible", isEligibleForReturn);
                    itemMap.put("returnDeadline", earliestDeadline);

                    itemsList.add(itemMap);
                }
                sellerOrdersList.add(soMap);
            }

            orderMap.put("subtotalAmount", totalGross);
            orderMap.put("discountAmount", totalDiscount);
            orderMap.put("status", o.getStatus().name());

            String statusLabel = switch (o.getStatus()) {
                case PENDING -> "Chờ xác nhận & thanh toán";
                case AWAITING_PAYMENT -> "Đang chờ thanh toán";
                case PAID -> "Đã thanh toán";
                case PROCESSING -> "Đang xử lý & đóng gói";
                case COMPLETED -> anyDelivered ? "Giao hàng thành công (Bảo hộ 14 ngày)" : "Hoàn tất";
                case CANCELLED -> "Đã hủy";
                case EXPIRED -> "Hết hạn thanh toán";
                default -> o.getStatus().name();
            };
            String statusColor = switch (o.getStatus()) {
                case PENDING, AWAITING_PAYMENT -> "#F59E0B";
                case PAID, PROCESSING -> "#3B82F6";
                case COMPLETED -> "#10B981";
                case CANCELLED, EXPIRED -> "#EF4444";
                default -> "#6B7280";
            };
            int timelineStep = switch (o.getStatus()) {
                case PENDING, AWAITING_PAYMENT -> 1;
                case PAID -> 2;
                case PROCESSING -> 3;
                case COMPLETED -> 4;
                default -> 1;
            };

            orderMap.put("statusLabel", statusLabel);
            orderMap.put("statusColor", statusColor);
            orderMap.put("timelineStep", timelineStep);
            orderMap.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
            orderMap.put("updatedAt", o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : "");
            orderMap.put("items", itemsList);
            orderMap.put("sellerOrders", sellerOrdersList);
            orderMap.put("returnDeadline", earliestDeadline);

            String storeName = "SCANMS Mall";
            String firstStoreId = !sellerOrders.isEmpty() ? sellerOrders.get(0).getStoreId() : "STORE_DEFAULT";
            if (!sellerOrders.isEmpty()) {
                storeName = "Gian hàng " + firstStoreId;
            }
            orderMap.put("store", Map.of(
                    "id", firstStoreId,
                    "name", storeName,
                    "slug", "store-" + firstStoreId
            ));

            resultOrders.add(orderMap);
        }

        return Map.of("orders", resultOrders);
    }
}
