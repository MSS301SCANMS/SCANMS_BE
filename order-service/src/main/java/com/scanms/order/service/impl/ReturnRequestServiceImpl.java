package com.scanms.order.service.impl;

import com.scanms.order.client.PaymentClient;
import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.request.ReturnDecisionRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.ReturnRequest;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.ReturnRequestMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.service.ReturnRequestService;
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
public class ReturnRequestServiceImpl implements ReturnRequestService {
    private final ReturnRequestRepository repository;
    private final ReturnRequestMapper mapper;
    private final OrderItemRepository orderItemRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    public ReturnRequestResponse create(CreateReturnRequest request) {
        // 1. Validate OrderItem
        OrderItem orderItem = orderItemRepository.findById(request.orderItemId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_ITEM_NOT_FOUND, "OrderItem not found: " + request.orderItemId()));

        // 2. Validate SellerOrder
        SellerOrder sellerOrder = sellerOrderRepository.findById(orderItem.getSellerOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.SELLER_ORDER_NOT_FOUND, "SellerOrder not found: " + orderItem.getSellerOrderId()));

        // Check if delivered
        if (sellerOrder.getStatus() != SellerOrderStatus.DELIVERED) {
            throw new AppException(ErrorCode.ORDER_NOT_DELIVERED, "Cannot request return on an order that has not been delivered");
        }

        // Check 14-day window
        LocalDateTime now = LocalDateTime.now();
        if (sellerOrder.getReturnDeadline() != null && now.isAfter(sellerOrder.getReturnDeadline())) {
            throw new AppException(ErrorCode.RETURN_WINDOW_EXPIRED, "Return window has expired (14-day limit)");
        }

        // Check quantityToReturn (Partial return validation)
        int requestedQty = request.quantity() != null ? request.quantity() : 1;
        if (requestedQty <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Quantity to return must be positive");
        }

        List<ReturnRequest> existingRequests = repository.findByOrderItemId(orderItem.getOrderItemId());
        int alreadyReturnedOrRequested = existingRequests.stream()
                .filter(r -> r.getStatus() != ReturnRequestStatus.REJECTED && r.getStatus() != ReturnRequestStatus.CANCELLED)
                .mapToInt(ReturnRequest::getQuantity)
                .sum();

        if (alreadyReturnedOrRequested + requestedQty > orderItem.getQuantity()) {
            throw new AppException(ErrorCode.INVALID_RETURN_QUANTITY,
                    String.format("Requested quantity (%d) plus already requested/returned (%d) exceeds purchased quantity (%d)",
                            requestedQty, alreadyReturnedOrRequested, orderItem.getQuantity()));
        }

        // Calculate refund basis: (unitPrice * requestedQty) - (unitDiscount * requestedQty)
        long grossReturned = orderItem.getUnitPrice() * requestedQty;
        long unitDiscount = (orderItem.getQuantity() > 0 && orderItem.getDiscountAmountVnd() != null)
                ? (orderItem.getDiscountAmountVnd() / orderItem.getQuantity())
                : 0L;
        long calculatedRefund = Math.max(0L, grossReturned - (unitDiscount * requestedQty));

        ReturnRequest entity = ReturnRequest.builder()
                .orderItemId(orderItem.getOrderItemId())
                .quantity(requestedQty)
                .reason(request.reason())
                .evidenceRefs(request.evidenceRefs() != null ? request.evidenceRefs() : new HashMap<>())
                .status(ReturnRequestStatus.REQUESTED)
                .refundAmountVnd(calculatedRefund)
                .requestedAt(now)
                .build();

        return mapper.toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public ReturnRequestResponse processDecision(String returnRequestId, ReturnDecisionRequest decision) {
        ReturnRequest rr = repository.findById(returnRequestId)
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_REQUEST_NOT_FOUND, "ReturnRequest not found: " + returnRequestId));

        if (decision.status() == ReturnRequestStatus.APPROVED) {
            rr.setStatus(ReturnRequestStatus.APPROVED);
            rr.setDecisionReason(decision.decisionReason());
            rr.setResolvedAt(LocalDateTime.now());

            // Orchestrate Refund to Customer Wallet via Payment Service
            String idempotencyKey = "REFUND_" + rr.getReturnRequestId();
            try {
                Map<String, Object> refundPayload = new HashMap<>();
                refundPayload.put("type", "REFUND");
                refundPayload.put("direction", "CREDIT");
                refundPayload.put("amountVnd", rr.getRefundAmountVnd());
                refundPayload.put("status", "SUCCESS");
                refundPayload.put("referenceType", "RETURN_REQUEST");
                refundPayload.put("referenceId", rr.getReturnRequestId());
                refundPayload.put("idempotencyKey", idempotencyKey);
                refundPayload.put("description", "Refund for ReturnRequest #" + rr.getReturnRequestId());

                paymentClient.createWalletTransaction(refundPayload);
                log.info("Successfully called payment-service to refund wallet for request: {}", returnRequestId);
                rr.setStatus(ReturnRequestStatus.REFUNDED);
                rr.setRefundReference("REFUND_TX_" + rr.getReturnRequestId().substring(0, Math.min(8, rr.getReturnRequestId().length())));
            } catch (Exception e) {
                log.warn("Payment client call failed or payment-service is offline for refund: {}. Setting status to APPROVED.", e.getMessage());
                rr.setRefundReference("REFUND_OFFLINE_SYNC");
            }
        } else if (decision.status() == ReturnRequestStatus.REJECTED) {
            rr.setStatus(ReturnRequestStatus.REJECTED);
            rr.setDecisionReason(decision.decisionReason());
            rr.setResolvedAt(LocalDateTime.now());
        }

        return mapper.toResponse(repository.save(rr));
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RETURN_REQUEST_NOT_FOUND, "ReturnRequest not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> findByOrderItemId(String orderItemId) {
        return repository.findByOrderItemId(orderItemId).stream().map(mapper::toResponse).toList();
    }
}
