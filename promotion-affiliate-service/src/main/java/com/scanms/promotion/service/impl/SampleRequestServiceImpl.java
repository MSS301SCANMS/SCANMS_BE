package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.request.*;
import com.scanms.promotion.entity.SampleRequest;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SampleRequestServiceImpl implements SampleRequestService {
    private final SampleRequestRepository samples;
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository partnerships;
    private final VoucherAccess access;
    private final ReferralProductAccess catalog;

    @Override
    public SampleRequest create(SampleCreateRequest input) {
        String user = access.userId();
        var profile = profiles.lockByUserId(user).filter(p -> p.getApprovalStatus() == CollaboratorStatus.APPROVED)
                .orElseThrow(() -> fail(HttpStatus.FORBIDDEN, "Chỉ KOL đã được duyệt được xin hàng mẫu."));
        String id;
        try {
            id = UUID.fromString(input.requestId()).toString();
        } catch (IllegalArgumentException ex) {
            throw fail(HttpStatus.BAD_REQUEST, "Mã yêu cầu không hợp lệ.");
        }
        var existing = samples.findById(id);
        if (existing.isPresent()) {
            var row = existing.get();
            if (!user.equals(row.getCustomerId()) || !input.productId().equals(row.getProductId())
                    || !input.note().trim().equals(row.getNote()) || !input.shippingAddress().trim().equals(row.getShippingAddress())
                    || !input.recipientName().trim().equals(row.getRecipientName()) || !input.recipientPhone().trim().equals(row.getRecipientPhone()))
                throw fail(HttpStatus.CONFLICT, "Mã yêu cầu đã được dùng cho nội dung khác.");
            return row;
        }
        var product = catalog.product(input.productId());
        String store = Objects.toString(product.get("storeId"), "");
        if (!partnerships.existsByStoreIdAndCollaboratorIdAndProductIdAndStatusIn(store, profile.getCollaboratorId(), null, List.of("ACCEPTED"))
                && !partnerships.existsByStoreIdAndCollaboratorIdAndProductIdAndStatusIn(store, profile.getCollaboratorId(), input.productId(), List.of("ACCEPTED")))
            throw fail(HttpStatus.FORBIDDEN, "KOL cần có hợp tác được shop chấp nhận.");
        if (samples.existsByCollaboratorIdAndProductIdAndStatusIn(profile.getCollaboratorId(), input.productId(), List.of("PENDING", "APPROVED", "SHIPPED", "RECEIVED")))
            throw fail(HttpStatus.CONFLICT, "Sản phẩm đã có yêu cầu hàng mẫu còn hiệu lực.");
        var row = new SampleRequest();
        row.setId(id);
        row.setCustomerId(user);
        row.setCollaboratorId(profile.getCollaboratorId());
        row.setStoreId(store);
        row.setProductId(input.productId());
        row.setStatus("PENDING");
        row.setNote(input.note().trim());
        row.setRecipientName(input.recipientName().trim());
        row.setRecipientPhone(input.recipientPhone().trim());
        row.setShippingAddress(input.shippingAddress().trim());
        row.setCreatedAt(Instant.now());
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", input.productId());
        snapshot.put("title", Objects.toString(product.get("title"), Objects.toString(product.get("name"), "Sản phẩm")));
        snapshot.put("price", product.getOrDefault("priceVnd", null));
        snapshot.put("thumbnail", product.get("imageUrl"));
        row.setProduct(snapshot);
        return samples.saveAndFlush(row);
    }

    @Override
    @Transactional(readOnly = true)
    public SampleRequest get(String id) {
        var row = samples.findById(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hàng mẫu."));
        if (!access.userId().equals(row.getCustomerId())) access.requireStoreOwner(row.getStoreId());
        return row;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleRequest> list(boolean shop) {
        return shop ? samples.findByStoreIdInOrderByCreatedAtDesc(access.ownedStoreIds()) : samples.findByCustomerIdOrderByCreatedAtDesc(access.userId());
    }

    @Override
    public SampleRequest decide(String id, String action, SampleDecisionRequest input) {
        var row = samples.locked(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hàng mẫu."));
        String actor = access.userId();
        boolean customer = actor.equals(row.getCustomerId());
        if (!customer) access.requireStoreOwner(row.getStoreId());
        if (!Objects.equals(input.expectedVersion(), row.getVersion()))
            throw fail(HttpStatus.CONFLICT, "Yêu cầu đã thay đổi. Vui lòng tải lại.");
        String next = switch (action) {
            case "approve" -> "APPROVED";
            case "reject" -> "REJECTED";
            case "cancel" -> "CANCELLED";
            case "ship" -> "SHIPPED";
            case "receive" -> "RECEIVED";
            default -> throw fail(HttpStatus.BAD_REQUEST, "Thao tác không hợp lệ.");
        };
        if (Set.of("approve", "reject", "ship").contains(action) == customer)
            throw fail(HttpStatus.FORBIDDEN, "Bạn không có quyền xử lý bước này.");
        if ("ship".equals(action)) {
            if (!"APPROVED".equals(row.getStatus()) || input.carrierName() == null || input.carrierName().isBlank() || input.trackingNumber() == null || input.trackingNumber().isBlank())
                throw fail(HttpStatus.CONFLICT, "Cần yêu cầu đã duyệt và mã vận đơn thực tế.");
            row.setCarrier(input.carrierName().trim());
            row.setTrackingCode(input.trackingNumber().trim());
            row.setShippedAt(Instant.now());
        } else if ("receive".equals(action)) {
            if (!"SHIPPED".equals(row.getStatus())) throw fail(HttpStatus.CONFLICT, "Hàng chưa được gửi.");
            row.setReceivedAt(Instant.now());
        } else if (!"PENDING".equals(row.getStatus()))
            throw fail(HttpStatus.CONFLICT, "Yêu cầu không còn chờ phản hồi.");
        if ("reject".equals(action) && (input.response() == null || input.response().isBlank()))
            throw fail(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối.");
        row.setResponse(input.response());
        row.setStatus(next);
        return samples.saveAndFlush(row);
    }

    private ResponseStatusException fail(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
