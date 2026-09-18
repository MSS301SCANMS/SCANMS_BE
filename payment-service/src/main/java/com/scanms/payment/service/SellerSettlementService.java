package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreateSellerSettlementRequest;
import com.scanms.payment.dto.response.SellerSettlementResponse;
import java.util.*;

public interface SellerSettlementService {
    SellerSettlementResponse create(CreateSellerSettlementRequest request);
    SellerSettlementResponse getById(UUID id);
    List<SellerSettlementResponse> findAll();
}
