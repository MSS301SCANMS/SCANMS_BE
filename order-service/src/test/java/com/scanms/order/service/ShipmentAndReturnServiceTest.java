package com.scanms.order.service;

import com.scanms.order.client.PaymentClient;
import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.constant.ShipmentStatus;
import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.request.ReturnDecisionRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.ReturnRequest;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.entity.Shipment;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.ReturnRequestMapper;
import com.scanms.order.mapper.ShipmentMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.impl.ReturnRequestServiceImpl;
import com.scanms.order.service.impl.ShipmentServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipmentAndReturnServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;
    @Mock
    private ShipmentMapper shipmentMapper;
    @Mock
    private SellerOrderRepository sellerOrderRepository;

    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private ReturnRequestMapper returnRequestMapper;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PaymentClient paymentClient;

    @Test
    @DisplayName("Shipment DELIVERED must set deliveredAt and returnDeadline = deliveredAt + 14 days")
    void testShipmentDeliveredSets14DayDeadline() {
        ShipmentServiceImpl shipmentService = new ShipmentServiceImpl(shipmentRepository, shipmentMapper, sellerOrderRepository);

        Shipment shipment = Shipment.builder()
                .shipmentId("ship-1")
                .sellerOrderId("so-1")
                .status(ShipmentStatus.SHIPPED)
                .build();

        SellerOrder sellerOrder = SellerOrder.builder()
                .sellerOrderId("so-1")
                .status(SellerOrderStatus.SHIPPED)
                .build();

        when(shipmentRepository.findById("ship-1")).thenReturn(Optional.of(shipment));
        when(sellerOrderRepository.findById("so-1")).thenReturn(Optional.of(sellerOrder));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        shipmentService.updateShipmentStatus("ship-1", ShipmentStatus.DELIVERED, "GHN", "TRACK123", null);

        // Assert
        assertEquals(ShipmentStatus.DELIVERED, shipment.getStatus());
        assertNotNull(shipment.getDeliveredAt());

        // Verify SellerOrder updated with 14-day returnDeadline
        ArgumentCaptor<SellerOrder> soCaptor = ArgumentCaptor.forClass(SellerOrder.class);
        verify(sellerOrderRepository).save(soCaptor.capture());
        SellerOrder savedSo = soCaptor.getValue();

        assertEquals(SellerOrderStatus.DELIVERED, savedSo.getStatus());
        assertNotNull(savedSo.getDeliveredAt());
        assertNotNull(savedSo.getReturnDeadline());

        // Must be exactly 14 days later
        assertEquals(savedSo.getDeliveredAt().plusDays(14), savedSo.getReturnDeadline());
    }

    @Test
    @DisplayName("Return request before delivery should throw ORDER_NOT_DELIVERED")
    void testReturnBeforeDeliveryRejected() {
        ReturnRequestServiceImpl returnService = new ReturnRequestServiceImpl(
                returnRequestRepository, returnRequestMapper, orderItemRepository, sellerOrderRepository, paymentClient
        );

        OrderItem item = OrderItem.builder().orderItemId("item-1").sellerOrderId("so-1").quantity(3).unitPrice(100000L).build();
        SellerOrder so = SellerOrder.builder().sellerOrderId("so-1").status(SellerOrderStatus.SHIPPED).build();

        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(sellerOrderRepository.findById("so-1")).thenReturn(Optional.of(so));

        CreateReturnRequest req = new CreateReturnRequest("item-1", 1, "Hỏng", Map.of(), null, null, null, null, null, null);

        AppException ex = assertThrows(AppException.class, () -> returnService.create(req));
        assertEquals(ErrorCode.ORDER_NOT_DELIVERED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Return request after 14-day returnDeadline should throw RETURN_WINDOW_EXPIRED")
    void testReturnAfter14DaysRejected() {
        ReturnRequestServiceImpl returnService = new ReturnRequestServiceImpl(
                returnRequestRepository, returnRequestMapper, orderItemRepository, sellerOrderRepository, paymentClient
        );

        OrderItem item = OrderItem.builder().orderItemId("item-1").sellerOrderId("so-1").quantity(3).unitPrice(100000L).build();
        // Deadline was yesterday
        SellerOrder so = SellerOrder.builder()
                .sellerOrderId("so-1")
                .status(SellerOrderStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now().minusDays(15))
                .returnDeadline(LocalDateTime.now().minusDays(1))
                .build();

        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(sellerOrderRepository.findById("so-1")).thenReturn(Optional.of(so));

        CreateReturnRequest req = new CreateReturnRequest("item-1", 1, "Đổi ý", Map.of(), null, null, null, null, null, null);

        AppException ex = assertThrows(AppException.class, () -> returnService.create(req));
        assertEquals(ErrorCode.RETURN_WINDOW_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Partial return exceeding purchased quantity should throw INVALID_RETURN_QUANTITY")
    void testPartialReturnExceedingQuantityRejected() {
        ReturnRequestServiceImpl returnService = new ReturnRequestServiceImpl(
                returnRequestRepository, returnRequestMapper, orderItemRepository, sellerOrderRepository, paymentClient
        );

        // Bought 3 items
        OrderItem item = OrderItem.builder()
                .orderItemId("item-1")
                .sellerOrderId("so-1")
                .quantity(3)
                .unitPrice(100000L)
                .discountAmountVnd(30000L)
                .build();

        SellerOrder so = SellerOrder.builder()
                .sellerOrderId("so-1")
                .status(SellerOrderStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now().minusDays(2))
                .returnDeadline(LocalDateTime.now().plusDays(12))
                .build();

        // Previously returned 2 items
        ReturnRequest prevRequest = ReturnRequest.builder()
                .returnRequestId("ret-prev")
                .orderItemId("item-1")
                .quantity(2)
                .status(ReturnRequestStatus.APPROVED)
                .build();

        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(sellerOrderRepository.findById("so-1")).thenReturn(Optional.of(so));
        when(returnRequestRepository.findByOrderItemId("item-1")).thenReturn(List.of(prevRequest));

        // Attempting to return 2 more (2 + 2 = 4 > 3)
        CreateReturnRequest req = new CreateReturnRequest("item-1", 2, "Lỗi thêm", Map.of(), null, null, null, null, null, null);

        AppException ex = assertThrows(AppException.class, () -> returnService.create(req));
        assertEquals(ErrorCode.INVALID_RETURN_QUANTITY, ex.getErrorCode());
    }

    @Test
    @DisplayName("Valid partial return within 14 days should calculate correct refund amount (net basis)")
    void testValidPartialReturnRefundCalculation() {
        ReturnRequestServiceImpl returnService = new ReturnRequestServiceImpl(
                returnRequestRepository, returnRequestMapper, orderItemRepository, sellerOrderRepository, paymentClient
        );

        // Bought 3 items @ 100k = 300k gross. Discount = 30k (10k per item). Net paid = 270k.
        OrderItem item = OrderItem.builder()
                .orderItemId("item-1")
                .sellerOrderId("so-1")
                .quantity(3)
                .unitPrice(100000L)
                .grossAmountVnd(300000L)
                .discountAmountVnd(30000L)
                .netPaidAmountVnd(270000L)
                .build();

        SellerOrder so = SellerOrder.builder()
                .sellerOrderId("so-1")
                .status(SellerOrderStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now().minusDays(1))
                .returnDeadline(LocalDateTime.now().plusDays(13))
                .build();

        when(orderItemRepository.findById("item-1")).thenReturn(Optional.of(item));
        when(sellerOrderRepository.findById("so-1")).thenReturn(Optional.of(so));
        when(returnRequestRepository.findByOrderItemId("item-1")).thenReturn(Collections.emptyList());

        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(i -> {
            ReturnRequest rr = i.getArgument(0);
            rr.setReturnRequestId("ret-new-1");
            return rr;
        });

        // Customer wants to return 1 item
        CreateReturnRequest req = new CreateReturnRequest("item-1", 1, "Rách chỉ", Map.of(), null, null, null, null, null, null);

        returnService.create(req);

        ArgumentCaptor<ReturnRequest> captor = ArgumentCaptor.forClass(ReturnRequest.class);
        verify(returnRequestRepository).save(captor.capture());
        ReturnRequest saved = captor.getValue();

        assertEquals(1, saved.getQuantity());
        assertEquals(ReturnRequestStatus.REQUESTED, saved.getStatus());
        // Refund amount basis: 1 * (100.000 - 10.000) = 90.000đ
        assertEquals(90000L, saved.getRefundAmountVnd());
    }

    @Test
    @DisplayName("Approving ReturnRequest triggers Payment wallet refund orchestration")
    void testApproveReturnTriggersPaymentRefund() {
        ReturnRequestServiceImpl returnService = new ReturnRequestServiceImpl(
                returnRequestRepository, returnRequestMapper, orderItemRepository, sellerOrderRepository, paymentClient
        );

        ReturnRequest rr = ReturnRequest.builder()
                .returnRequestId("ret-123")
                .orderItemId("item-1")
                .quantity(1)
                .refundAmountVnd(90000L)
                .status(ReturnRequestStatus.REQUESTED)
                .build();

        when(returnRequestRepository.findById("ret-123")).thenReturn(Optional.of(rr));
        when(returnRequestRepository.save(any(ReturnRequest.class))).thenAnswer(i -> i.getArgument(0));

        ReturnDecisionRequest decision = new ReturnDecisionRequest(ReturnRequestStatus.APPROVED, "Hang dung loi", 90000L, null);

        returnService.processDecision("ret-123", decision);

        // Verify paymentClient was called with idempotency key
        verify(paymentClient).createWalletTransaction(argThat(map ->
                "REFUND".equals(map.get("type")) &&
                "CREDIT".equals(map.get("direction")) &&
                Long.valueOf(90000L).equals(map.get("amountVnd")) &&
                "REFUND_ret-123".equals(map.get("idempotencyKey"))
        ));

        assertEquals(ReturnRequestStatus.REFUNDED, rr.getStatus());
        assertNotNull(rr.getRefundReference());
    }
}
