package com.scanms.user.service;

import com.scanms.user.dto.request.LoginRequest;
import com.scanms.user.dto.request.RegisterRequest;
import com.scanms.user.dto.response.AuthResponse;

public interface AuthService {
    void register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    void removeRoleFromUser(String userId, String roleName);
    void createNewRole(String roleName);
    void changePassword(String oldPassword, String newPassword);
    void logout(String refreshToken);
    AuthResponse refreshToken(String refreshToken);
}
