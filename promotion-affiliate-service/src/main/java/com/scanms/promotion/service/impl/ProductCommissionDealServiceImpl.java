package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.entity.CommissionPolicy;
import com.scanms.promotion.entity.ProductCommissionDeal;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.repository.ProductCommissionDealRepository;
import com.scanms.promotion.service.CollaborationAccess;
import com.scanms.promotion.service.CommissionPolicyService;
import com.scanms.promotion.service.ProductCommissionDealService;
import com.scanms.promotion.service.ReferralProductAccess;
import com.scanms.promotion.service.VoucherAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cài đặt chi tiết nghiệp vụ đề xuất, xét duyệt và tính toán tỷ lệ hoa hồng riêng cho từng sản phẩm.
 * Sử dụng Clean Architecture, Lombok @RequiredArgsConstructor và khóa bi-directional an toàn.
 */
@Service
@RequiredArgsConstructor
public class ProductCommissionDealServiceImpl implements ProductCommissionDealService {

    private final ProductCommissionDealRepository deals;
    private final CollaboratorProfileRepository profiles;
    private final CommissionPolicyService policies;
    private final VoucherAccess access;
    private final ReferralProductAccess catalog;
    private final CollaborationAccess collaboration;

    @Override
    @Transactional(readOnly = true)
    public List<ProductCommissionDeal> mine() {
        var profile = profile(false);
        return deals.findByCollaboratorIdOrderByCreatedAtDesc(profile.getCollaboratorId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCommissionDeal> shop(Jwt jwt) {
        access.userId();
        var ids = collaboration.stores(jwt, false).stream()
                .map(s -> Objects.toString(s.get("storeId")))
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return deals.findByStoreIdInOrderByCreatedAtDesc(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Rate quote(String productId) {
        var profile = profile(false);
        var product = catalog.product(productId);
        String storeId = Objects.toString(product.get("storeId"), "");
        var policy = policy(profile.getCollaboratorId(), productId, storeId);
        return rate(profile.getCollaboratorId(), productId, storeId, Instant.now(), policy);
    }

    @Override
    @Transactional
    public ProductCommissionDeal propose(String productId, int points, String message) {
        if (points < 1 || points > 3) {
            throw fail(HttpStatus.BAD_REQUEST, "Chỉ được đề xuất tăng thêm 1, 2 hoặc 3 điểm phần trăm.");
        }
        String text = message == null ? "" : message.trim();
        if (text.length() < 10 || text.length() > 2000) {
            throw fail(HttpStatus.BAD_REQUEST, "Lời đề xuất cần từ 10 đến 2.000 ký tự.");
        }

        var profile = profile(true);
        var product = catalog.product(productId);
        String storeId = Objects.toString(product.get("storeId"), "");
        if (storeId.isBlank()) {
            throw fail(HttpStatus.CONFLICT, "Chưa xác định được gian hàng của sản phẩm.");
        }
        if (access.ownsStore(storeId)) {
            throw fail(HttpStatus.FORBIDDEN, "Không thể tự đề xuất hoa hồng cho sản phẩm của mình.");
        }
        if (deals.existsByCollaboratorIdAndProductIdAndStatus(profile.getCollaboratorId(), productId, "PENDING")) {
            throw fail(HttpStatus.CONFLICT, "Sản phẩm đã có đề xuất chờ shop phản hồi.");
        }

        var policy = policy(profile.getCollaboratorId(), productId, storeId);
        validateTotal(policy.getCommissionRate(), points);

        var deal = new ProductCommissionDeal();
        deal.setCollaboratorId(profile.getCollaboratorId());
        deal.setStoreId(storeId);
        deal.setProductId(productId);
        deal.setProductName(Objects.toString(product.get("title"), Objects.toString(product.get("name"), "Sản phẩm")));
        deal.setImageUrl(Objects.toString(product.get("imageUrl"), null));
        deal.setStoreName(product.get("store") instanceof Map<?, ?> s ? Objects.toString(s.get("name"), "Gian hàng") : "Gian hàng");
        deal.setCollaboratorName(profile.getApplicationData() == null ? "KOL" : Objects.toString(profile.getApplicationData().get("channelName"), "KOL"));
        deal.setExtraPoints(points);
        deal.setBaseRate(policy.getCommissionRate());
        deal.setBasePolicyId(policy.getPolicyId());
        deal.setMessage(text);
        deal.setStatus("PENDING");
        deal.setCreatedAt(Instant.now());

        return deals.saveAndFlush(deal);
    }

    @Override
    @Transactional
    public ProductCommissionDeal respond(String id, String decision, String response) {
        // Lock theo đúng thứ tự profile trước, deal sau để tránh deadlock
        var target = deals.findById(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy đề xuất."));
        access.requireStoreOwner(target.getStoreId());
        var profile = profiles.lockById(target.getCollaboratorId()).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy KOL."));
        var d = deals.locked(id).orElseThrow();

        if (!Set.of("APPROVED", "REJECTED", "ENDED").contains(decision)) {
            throw fail(HttpStatus.BAD_REQUEST, "Phản hồi không hợp lệ.");
        }
        if (decision.equals(d.getStatus())) {
            return d;
        }
        if (response != null && response.trim().length() > 1000) {
            throw fail(HttpStatus.BAD_REQUEST, "Phản hồi tối đa 1.000 ký tự.");
        }

        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if ("ENDED".equals(decision)) {
            if (!"APPROVED".equals(d.getStatus())) {
                throw fail(HttpStatus.CONFLICT, "Chỉ có thể kết thúc deal đang áp dụng.");
            }
            d.setEndedAt(now);
        } else {
            if (!"PENDING".equals(d.getStatus())) {
                throw fail(HttpStatus.CONFLICT, "Đề xuất đã được xử lý. Vui lòng cập nhật.");
            }
            if ("APPROVED".equals(decision)) {
                if (profile.getApprovalStatus() != CollaboratorStatus.APPROVED) {
                    throw fail(HttpStatus.CONFLICT, "Hồ sơ KOL chưa hoạt động.");
                }
                var currentProduct = catalog.product(d.getProductId());
                if (!d.getStoreId().equals(currentProduct.get("storeId"))) {
                    throw fail(HttpStatus.CONFLICT, "Sản phẩm không còn thuộc gian hàng này.");
                }
                var policy = policy(d.getCollaboratorId(), d.getProductId(), d.getStoreId());
                if (policy.getCommissionRate().compareTo(d.getBaseRate()) != 0) {
                    throw fail(HttpStatus.CONFLICT, "Mức hoa hồng cơ bản đã thay đổi. Hãy từ chối và yêu cầu KOL gửi đề xuất mới.");
                }
                validateTotal(policy.getCommissionRate(), d.getExtraPoints());
                for (var previous : deals.findByCollaboratorIdAndProductIdOrderByApprovedAtDesc(d.getCollaboratorId(), d.getProductId())) {
                    if ("APPROVED".equals(previous.getStatus())) {
                        previous.setStatus("SUPERSEDED");
                        previous.setEndedAt(now);
                    }
                }
                d.setBaseRate(policy.getCommissionRate());
                d.setBasePolicyId(policy.getPolicyId());
                d.setTotalRate(policy.getCommissionRate().add(BigDecimal.valueOf(d.getExtraPoints())));
                d.setApprovedAt(now);
            }
        }
        d.setStatus(decision);
        d.setResponse(response == null ? null : response.trim());
        d.setRespondedAt(now);

        return deals.saveAndFlush(d);
    }

    @Override
    @Transactional
    public ProductCommissionDeal cancel(String id) {
        var profile = profile(true);
        var d = deals.locked(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy đề xuất."));
        if (!profile.getCollaboratorId().equals(d.getCollaboratorId())) {
            throw fail(HttpStatus.FORBIDDEN, "Đề xuất không thuộc tài khoản của bạn.");
        }
        if ("CANCELLED".equals(d.getStatus())) {
            return d;
        }
        if (!"PENDING".equals(d.getStatus())) {
            throw fail(HttpStatus.CONFLICT, "Chỉ được thu hồi đề xuất đang chờ.");
        }
        d.setStatus("CANCELLED");
        d.setRespondedAt(Instant.now());
        return d;
    }

    @Override
    @Transactional(readOnly = true)
    public Rate rate(String collaboratorId, String productId, String storeId, Instant orderCreatedAt, CommissionPolicy fallback) {
        if (orderCreatedAt != null) {
            for (var d : deals.findByCollaboratorIdAndProductIdOrderByApprovedAtDesc(collaboratorId, productId)) {
                if (!Objects.equals(storeId, d.getStoreId()) || d.getApprovedAt() == null || d.getTotalRate() == null) {
                    continue;
                }
                if (!Set.of("APPROVED", "SUPERSEDED", "ENDED").contains(d.getStatus())) {
                    continue;
                }
                if (!d.getApprovedAt().isAfter(orderCreatedAt) && (d.getEndedAt() == null || orderCreatedAt.isBefore(d.getEndedAt()))) {
                    return new Rate(
                            d.getBaseRate(),
                            d.getExtraPoints(),
                            d.getTotalRate(),
                            d.getId(),
                            d.getBasePolicyId(),
                            fallback.getCommissionRate()
                    );
                }
            }
        }
        return new Rate(
                fallback.getCommissionRate(),
                0,
                fallback.getCommissionRate(),
                null,
                fallback.getPolicyId(),
                fallback.getCommissionRate()
        );
    }

    private CollaboratorProfile profile(boolean lock) {
        String userId = access.userId();
        var p = (lock ? profiles.lockByUserId(userId) : profiles.findByUserId(userId))
                .orElseThrow(() -> fail(HttpStatus.FORBIDDEN, "Bạn chưa có hồ sơ KOL được duyệt."));
        if (p.getApprovalStatus() != CollaboratorStatus.APPROVED) {
            throw fail(HttpStatus.FORBIDDEN, "Hồ sơ KOL chưa được duyệt hoặc đã tạm ngừng.");
        }
        return p;
    }

    private CommissionPolicy policy(String collaborator, String product, String store) {
        var p = policies.resolvePolicy(collaborator, null, product, store);
        if (p == null) {
            throw fail(HttpStatus.CONFLICT, "Chưa có chính sách hoa hồng đang áp dụng.");
        }
        return p;
    }

    private void validateTotal(BigDecimal base, int points) {
        if (base == null || base.signum() < 0 || base.add(BigDecimal.valueOf(points)).compareTo(BigDecimal.valueOf(100)) > 0) {
            throw fail(HttpStatus.CONFLICT, "Tổng hoa hồng không được vượt quá 100%.");
        }
    }

    private ResponseStatusException fail(HttpStatus status, String text) {
        return new ResponseStatusException(status, text);
    }
}
