package com.scanms.order.service;

import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import java.util.*;

import com.scanms.order.dto.request.ReturnDecisionRequest;

public interface ReturnRequestService {
    ReturnRequestResponse create(CreateReturnRequest request);
    ReturnRequestResponse getById(String id);
    List<ReturnRequestResponse> findAll();
    ReturnRequestResponse processDecision(String returnRequestId, ReturnDecisionRequest decision);
    List<ReturnRequestResponse> findByOrderItemId(String orderItemId);
}
