package com.scanms.order.service;

import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import java.util.*;

public interface ReturnRequestService {
    ReturnRequestResponse create(CreateReturnRequest request);
    ReturnRequestResponse getById(UUID id);
    List<ReturnRequestResponse> findAll();
}
