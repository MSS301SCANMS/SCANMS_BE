package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import java.util.*;

public interface CollaboratorService {
    CollaboratorProfileResponse create(ApplyCollaboratorRequest request);
    CollaboratorProfileResponse getById(String id);
    List<CollaboratorProfileResponse> findAll();

    /** MANAGER/ADMIN duyệt KOL — chuyển trạng thái sang APPROVED */
    CollaboratorProfileResponse approve(String id);

    /** MANAGER/ADMIN từ chối KOL — chuyển trạng thái sang REJECTED */
    CollaboratorProfileResponse reject(String id, String reason);
}
