package com.scanms.product.service;

import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.request.UpdateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;

import java.util.List;

public interface FeedbackService {
    List<FeedbackResponse> findMine(List<String> orderItemIds);

    FeedbackResponse create(CreateFeedbackRequest request);

    FeedbackResponse getById(String id);

    List<FeedbackResponse> findAll();

    FeedbackResponse updateOwn(String id, UpdateFeedbackRequest request);

    FeedbackResponse removeOwn(String id);

    FeedbackResponse moderate(String id, FeedbackStatus status);
}

