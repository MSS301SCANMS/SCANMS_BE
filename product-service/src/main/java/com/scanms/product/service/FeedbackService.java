package com.scanms.product.service;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import java.util.*;

public interface FeedbackService {
    FeedbackResponse create(CreateFeedbackRequest request);
    FeedbackResponse getById(String id);
    List<FeedbackResponse> findAll();
}

