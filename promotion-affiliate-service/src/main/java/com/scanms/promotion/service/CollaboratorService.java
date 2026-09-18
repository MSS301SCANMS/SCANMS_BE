package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import java.util.*;

public interface CollaboratorService {
    CollaboratorProfileResponse create(ApplyCollaboratorRequest request);
    CollaboratorProfileResponse getById(UUID id);
    List<CollaboratorProfileResponse> findAll();
}
