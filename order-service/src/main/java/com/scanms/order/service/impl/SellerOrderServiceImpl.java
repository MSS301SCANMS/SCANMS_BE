package com.scanms.order.service.impl;

import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.OrderItemResponse;
import com.scanms.order.dto.response.SellerOrderResponse;
import com.scanms.order.dto.response.SellerOrderSummaryResponse;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.entity.Shipment;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.OrderItemMapper;
import com.scanms.order.mapper.SellerOrderMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.SellerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerOrderServiceImpl implements SellerOrderService {
    private final SellerOrderRepository repository;
    private final SellerOrderMapper mapper;
    private final OrderItemRepository orderItemRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderItemMapper orderItemMapper;

    public SellerOrderResponse create(CreateSellerOrderRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public SellerOrderResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.SELLER_ORDER_NOT_FOUND, "SellerOrder not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerOrderResponse> findByStore(String storeId, SellerOrderStatus status) {
        if (storeId == null || storeId.isBlank()) {
            return Collections.emptyList();
        }
        if (status != null) {
            return repository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, status)
                    .stream().map(mapper::toResponse).toList();
        }
        return repository.findByStoreIdOrderByCreatedAtDesc(storeId)
                .stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SellerOrderSummaryResponse getSummaryById(String sellerOrderId) {
        SellerOrder so = repository.findById(sellerOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.SELLER_ORDER_NOT_FOUND, "SellerOrder not found: " + sellerOrderId));

        List<OrderItem> items = orderItemRepository.findBySellerOrderId(sellerOrderId);
        List<OrderItemResponse> itemResponses = items.stream().map(orderItemMapper::toResponse).toList();

        String shipmentId = shipmentRepository.findBySellerOrderId(sellerOrderId)
                .map(Shipment::getShipmentId)
                .orElse(null);

        return new SellerOrderSummaryResponse(
                so.getSellerOrderId(),
                so.getOrderId(),
                so.getStoreId(),
                so.getStatus(),
                so.getTotalsSnapshot(),
                shipmentId,
                so.getDeliveredAt(),
                so.getReturnDeadline(),
                itemResponses
        );
    }

    @Override
    @Transactional
    public SellerOrderResponse updateStatus(String sellerOrderId, SellerOrderStatus newStatus) {
        SellerOrder so = repository.findById(sellerOrderId)
                .orElseThrow(() -> new AppException(ErrorCode.SELLER_ORDER_NOT_FOUND, "SellerOrder not found: " + sellerOrderId));

        so.setStatus(newStatus);
        return mapper.toResponse(repository.save(so));
    }
}
