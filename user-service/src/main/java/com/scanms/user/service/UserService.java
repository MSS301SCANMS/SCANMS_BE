package com.scanms.user.service;

import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.response.UserResponse;

public interface UserService {
    UserResponse getCurrentUser(String identitySubject);
    UserResponse updateCurrentUser(String identitySubject, UpdateUserProfileRequest request);
}
