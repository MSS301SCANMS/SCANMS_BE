package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.entity.CommissionPolicy;
import java.util.*;

public interface CommissionPolicyService {
    CommissionPolicyResponse create(CreateCommissionPolicyRequest request);
    CommissionPolicyResponse getById(String id);
    List<CommissionPolicyResponse> findAll();

    /**
     * Chọn CommissionPolicy phù hợp theo thứ tự ưu tiên:
     * COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM
     *
     * @param collaboratorId ID của KOL (từ ReferralLink)
     * @param livestreamId   ID buổi live (có thể null)
     * @param productId      ID sản phẩm (từ ReferralLink hoặc OrderItem)
     * @param storeId        ID store (từ SellerOrder)
     * @return CommissionPolicy phù hợp nhất, hoặc null nếu không tìm thấy
     */
    CommissionPolicy resolvePolicy(String collaboratorId, String livestreamId, String productId, String storeId);
}

