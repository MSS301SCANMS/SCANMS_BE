package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.SampleCreateRequest;
import com.scanms.promotion.dto.request.SampleDecisionRequest;
import com.scanms.promotion.entity.SampleRequest;

import java.util.List;

public interface SampleRequestService {
    SampleRequest create(SampleCreateRequest input);

    SampleRequest get(String id);

    List<SampleRequest> list(boolean shop);

    SampleRequest decide(String id, String action, SampleDecisionRequest input);
}
