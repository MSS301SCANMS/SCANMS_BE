package com.scanms.user.service;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.dto.PageResponse;
import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.request.UpdateUserStatusRequest;
import com.scanms.user.dto.response.UserResponse;

public interface UserService {
    UserResponse getCurrentUser(String identitySubject);
    UserResponse updateCurrentUser(String identitySubject, UpdateUserProfileRequest request);

    // --- Danh cho man Quan tri "Tai khoan & Phan quyen" (chi ADMIN) ---

    /** Danh sach user toan san, co phan trang, tim kiem va loc theo role/status. */
    PageResponse<UserResponse> getAllUsersForAdmin(int page, int size, String search, RoleName role, UserStatus status);

    /** Xem chi tiet 1 user theo userId (khong phai identitySubject cua nguoi dang goi). */
    UserResponse getUserByIdForAdmin(String userId);

    /** Doi trang thai user (khoa/mo khoa tai khoan - soft delete). Khong cho phep doi voi tai khoan co role ADMIN. */
    UserResponse updateUserStatusForAdmin(String userId, UpdateUserStatusRequest request);
}
