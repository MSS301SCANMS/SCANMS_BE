package com.scanms.user.service;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.dto.PageResponse;
import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.request.UpdateUserStatusRequest;
import com.scanms.user.dto.response.UserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserService {
    UserResponse getCurrentUser(String identitySubject);

    UserResponse synchronizeCurrentUser(Jwt jwt);

    UserResponse updateCurrentUser(String identitySubject, UpdateUserProfileRequest request);

    // --- Danh cho man Quan tri "Tai khoan & Phan quyen" (chi ADMIN) ---

    /**
     * Danh sach user toan san, co phan trang, tim kiem va loc theo role/status.
     */
    PageResponse<UserResponse> getAllUsers(int page, int size, String search, RoleName role, UserStatus status);

    /**
     * Xem chi tiet 1 user theo userId (khong phai identitySubject cua nguoi dang goi).
     */
    UserResponse getUserById(String userId);

    /**
     * Doi trang thai user (khoa/mo khoa tai khoan - soft delete). Khong cho phep doi voi tai khoan co role ADMIN.
     */
    UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request);
}
