package com.scanms.user.service;

import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.response.UserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {
    UserResponse getCurrentUser(String identitySubject);
    UserResponse synchronizeCurrentUser(Jwt jwt);
    UserResponse updateCurrentUser(String identitySubject, UpdateUserProfileRequest request);
}
