package com.scanms.order.service;
import com.scanms.order.service.impl.OrderCancellationPolicyImpl;
import com.scanms.order.repository.*;
import com.scanms.order.entity.*;
import com.scanms.order.entity.Order;
import com.scanms.order.constant.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OrderCancellationPolicyTest {
    OrderRepository orders=mock(OrderRepository.class);SellerOrderRepository sellers=mock(SellerOrderRepository.class);ShipmentRepository shipments=mock(ShipmentRepository.class);
    OrderCancellationPolicy policy=new OrderCancellationPolicyImpl(orders,sellers,shipments);
    Order order;SellerOrder seller;
    @BeforeEach void setup(){order=Order.builder().orderId("order").status(OrderStatus.CANCELLED).build();seller=SellerOrder.builder().sellerOrderId("seller").status(SellerOrderStatus.CANCELLED).build();when(orders.findById("order")).thenReturn(Optional.of(order));when(sellers.findByOrderId("order")).thenReturn(List.of(seller));when(shipments.findBySellerOrderId("seller")).thenReturn(Optional.empty());}
    @Test void cancelledUndeliveredOrderIsEligibleButPaidOrMissingSellerIsNot(){assertTrue(policy.canRestoreResources("order"));order.setStatus(OrderStatus.PAID);assertFalse(policy.canRestoreResources("order"));order.setStatus(OrderStatus.CANCELLED);when(sellers.findByOrderId("order")).thenReturn(List.of());assertFalse(policy.canRestoreResources("order"));}
    @Test void deliveryHistoryBlocksRestorationEvenAfterStatusWasChangedToCancelled(){seller.setDeliveredAt(LocalDateTime.now());assertFalse(policy.canRestoreResources("order"));seller.setDeliveredAt(null);when(shipments.findBySellerOrderId("seller")).thenReturn(Optional.of(Shipment.builder().status(ShipmentStatus.FAILED).deliveredAt(Instant.now()).build()));assertFalse(policy.canRestoreResources("order"));}
    @Test void everySellerMustBeCancelledBeforeRestoringWholeOrderVouchers(){when(sellers.findByOrderId("order")).thenReturn(List.of(seller,SellerOrder.builder().sellerOrderId("second").status(SellerOrderStatus.SHIPPED).build()));assertFalse(policy.canRestoreResources("order"));}
}
