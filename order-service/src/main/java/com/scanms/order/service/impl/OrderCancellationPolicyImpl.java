package com.scanms.order.service.impl;

import com.scanms.order.service.OrderCancellationPolicy;
import com.scanms.order.repository.*;
import com.scanms.order.constant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class OrderCancellationPolicyImpl implements OrderCancellationPolicy {
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final ShipmentRepository shipments;
    @Transactional(readOnly=true)
    public boolean canRestoreResources(String orderId) {
        var order=orders.findById(orderId);
        if(order.isEmpty()||order.get().getStatus()!=OrderStatus.CANCELLED)return false;
        var rows=sellers.findByOrderId(orderId);
        if(rows.isEmpty())return false;
        for(var seller:rows){
            if(seller.getStatus()!=SellerOrderStatus.CANCELLED||seller.getDeliveredAt()!=null)return false;
            var shipment=shipments.findBySellerOrderId(seller.getSellerOrderId());
            if(shipment.isPresent()&&(shipment.get().getDeliveredAt()!=null||shipment.get().getStatus()==ShipmentStatus.DELIVERED))return false;
        }
        return true;
    }
}
