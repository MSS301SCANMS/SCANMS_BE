package com.scanms.order.service.impl;

import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.constant.ShipmentStatus;
import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.entity.Shipment;
import com.scanms.order.exception.*;
import com.scanms.order.mapper.ShipmentMapper;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository repository;
    private final ShipmentMapper mapper;
    private final SellerOrderRepository sellerOrderRepository;

    public ShipmentResponse create(CreateShipmentRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getBySellerOrderId(String sellerOrderId) {
        return repository.findBySellerOrderId(sellerOrderId).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found for seller order: " + sellerOrderId));
    }

    @Override
    @Transactional
    public ShipmentResponse updateShipmentStatus(
            String shipmentId,
            ShipmentStatus status,
            String carrier,
            String trackingCode,
            String failureReason) {
        Shipment shipment = repository.findById(shipmentId)
                .orElseThrow(() -> new AppException(ErrorCode.SHIPMENT_NOT_FOUND, "Shipment not found: " + shipmentId));

        shipment.setStatus(status);
        if (carrier != null && !carrier.isBlank()) shipment.setCarrier(carrier);
        if (trackingCode != null && !trackingCode.isBlank()) shipment.setTrackingCode(trackingCode);

        Instant nowInstant = Instant.now();
        LocalDateTime nowLocal = LocalDateTime.now();

        if (status == ShipmentStatus.SHIPPED) {
            shipment.setShippedAt(nowInstant);
            sellerOrderRepository.findById(shipment.getSellerOrderId()).ifPresent(so -> {
                so.setStatus(SellerOrderStatus.SHIPPED);
                sellerOrderRepository.save(so);
            });
        } else if (status == ShipmentStatus.FAILED) {
            shipment.setFailedAt(nowInstant);
            shipment.setFailureReason(failureReason);
        } else if (status == ShipmentStatus.DELIVERED) {
            shipment.setDeliveredAt(nowInstant);

            // Crucial: Update SellerOrder and compute 14-day return deadline!
            sellerOrderRepository.findById(shipment.getSellerOrderId()).ifPresent(so -> {
                so.setStatus(SellerOrderStatus.DELIVERED);
                so.setDeliveredAt(nowLocal);
                so.setReturnDeadline(nowLocal.plusDays(14));
                sellerOrderRepository.save(so);
                log.info("SellerOrder {} marked DELIVERED, return deadline set to: {}",
                        so.getSellerOrderId(), so.getReturnDeadline());
            });
        }

        return mapper.toResponse(repository.save(shipment));
    }
}
