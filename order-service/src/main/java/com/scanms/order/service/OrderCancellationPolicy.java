package com.scanms.order.service;
public interface OrderCancellationPolicy {
    boolean canRestoreResources(String orderId);
}
