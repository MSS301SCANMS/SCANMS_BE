package com.scanms.product.service;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import java.util.*;
import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.dto.request.UpdateFeedbackRequest;

public interface FeedbackService {
    FeedbackResponse create(CreateFeedbackRequest request);
    FeedbackResponse getById(String id);
    List<FeedbackResponse> findAll();
    FeedbackResponse updateOwn(String id, UpdateFeedbackRequest request);
    FeedbackResponse removeOwn(String id);
    FeedbackResponse moderate(String id, FeedbackStatus status);
}

