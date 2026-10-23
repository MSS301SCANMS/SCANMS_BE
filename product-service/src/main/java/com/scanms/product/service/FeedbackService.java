package com.scanms.product.service;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import java.util.*;

public interface FeedbackService {
    java.util.List<com.scanms.product.dto.response.FeedbackResponse> findMine(java.util.List<String> orderItemIds);
    FeedbackResponse create(CreateFeedbackRequest request);
    FeedbackResponse getById(String id);
    List<FeedbackResponse> findAll();
    FeedbackResponse updateOwn(String id, com.scanms.product.dto.request.UpdateFeedbackRequest request);
    FeedbackResponse removeOwn(String id);
    FeedbackResponse moderate(String id, com.scanms.product.constant.FeedbackStatus status);
}

