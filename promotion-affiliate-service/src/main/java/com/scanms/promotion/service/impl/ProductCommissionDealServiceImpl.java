package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.request.CommissionNegotiationRequest;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

    private final EntityManager em;

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
        if (deals.existsByCollaboratorIdAndProductIdAndStatus(profile.getCollaboratorId(), productId, "PENDING")
                || deals.existsByCollaboratorIdAndProductIdAndStatus(profile.getCollaboratorId(), productId, "COUNTERED")) {
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
        deal.setProposedByUserId(profile.getUserId());
        recordNegotiation(deal, profile.getUserId(), "PROPOSE", text);

        return deals.saveAndFlush(deal);
    }

    @Override
    @Transactional
    public ProductCommissionDeal respond(String id, String decision, String response) {
        return respond(id, decision, response, false);
    }

    private ProductCommissionDeal respond(String id, String decision, String response, boolean versionChecked) {
        // Lock theo đúng thứ tự profile trước, deal sau để tránh deadlock
        var target = deals.findById(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy đề xuất."));
        String actor = access.userId();
        boolean kolReply = "COUNTERED".equals(target.getStatus()) && profiles.findById(target.getCollaboratorId())
                .map(p -> actor.equals(p.getUserId())).orElse(false);
        if (!kolReply) access.requireStoreOwner(target.getStoreId());
        var profile = profiles.lockById(target.getCollaboratorId()).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy KOL."));
        var d = deals.locked(id).orElseThrow();
        if (!versionChecked && Set.of("PENDING", "COUNTERED").contains(d.getStatus()) && d.getNegotiationHistory() != null && d.getNegotiationHistory().size() > 1)
            throw fail(HttpStatus.CONFLICT, "Hãy mở đề xuất mới nhất trong chat để phản hồi.");
        if (!"ENDED".equals(decision) && actor.equals(d.getProposedByUserId()))
            throw fail(HttpStatus.FORBIDDEN, "Không thể tự chấp nhận hoặc từ chối đề xuất của mình.");

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
            if (!Set.of("PENDING", "COUNTERED").contains(d.getStatus())) {
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
        recordNegotiation(d, actor, decision, response);

        return deals.saveAndFlush(d);
    }

    @Override
    @Transactional
    public ProductCommissionDeal negotiate(String id, CommissionNegotiationRequest request) {
        var target = deals.findById(id).orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "Không tìm thấy đề xuất."));
        var profile = profiles.lockById(target.getCollaboratorId()).orElseThrow();
        String actor = access.userId();
        boolean kol = actor.equals(profile.getUserId());
        if (!kol) access.requireStoreOwner(target.getStoreId());
        if (profile.getApprovalStatus() != CollaboratorStatus.APPROVED)
            throw fail(HttpStatus.FORBIDDEN, "KOL chưa hoạt động.");
        em.refresh(target, LockModeType.PESSIMISTIC_WRITE);
        var d = target;
        if (!Objects.equals(request.expectedVersion(), d.getVersion()) || !Set.of("PENDING", "COUNTERED").contains(d.getStatus()))
            throw fail(HttpStatus.CONFLICT, "Đề xuất đã thay đổi. Vui lòng tải lại phiên bản mới nhất.");
        String proposer = d.getProposedByUserId() == null ? profile.getUserId() : d.getProposedByUserId();
        if (actor.equals(proposer)) throw fail(HttpStatus.FORBIDDEN, "Đề xuất đang chờ bên còn lại phản hồi.");
        if (!"COUNTER".equals(request.action()))
            return respond(id, "ACCEPT".equals(request.action()) ? "APPROVED" : "REJECTED", request.note(), true);
        if (request.extraPoints() == null || request.extraPoints() < 1 || request.extraPoints() > 3)
            throw fail(HttpStatus.BAD_REQUEST, "Mức tăng phải từ 1 đến 3 điểm phần trăm.");
        var policy = policy(d.getCollaboratorId(), d.getProductId(), d.getStoreId());
        validateTotal(policy.getCommissionRate(), request.extraPoints());
        d.setBaseRate(policy.getCommissionRate());
        d.setBasePolicyId(policy.getPolicyId());
        d.setExtraPoints(request.extraPoints());
        d.setProposedByUserId(actor);
        d.setStatus(kol ? "PENDING" : "COUNTERED");
        d.setResponse(request.note());
        d.setRespondedAt(Instant.now());
        recordNegotiation(d, actor, "COUNTER", request.note());
        return deals.saveAndFlush(d);
    }

    private void recordNegotiation(ProductCommissionDeal deal, String actor, String action, String note) {
        var history = new ArrayList<>(deal.getNegotiationHistory() == null ? List.<Map<String, Object>>of() : deal.getNegotiationHistory());
        if (history.size() >= 100) throw fail(HttpStatus.CONFLICT, "Đã đạt giới hạn thương lượng cho đề xuất này.");
        history.add(Map.of("actorId", actor, "action", action, "extraPoints", deal.getExtraPoints(),
                "baseRate", deal.getBaseRate(), "note", Objects.toString(note, ""), "at", Instant.now().toString()));
        deal.setNegotiationHistory(history);
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
