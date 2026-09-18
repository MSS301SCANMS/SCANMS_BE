package com.scanms.user.service.impl;

import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.mapper.UserMapper;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository repository;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String identitySubject) {
        return mapper.toResponse(find(identitySubject));
    }

    public UserResponse updateCurrentUser(String identitySubject, UpdateUserProfileRequest request) {
        User user = find(identitySubject);
        if (request.email() != null) user.setEmail(request.email());
        if (request.fullName() != null) user.setFullName(request.fullName());
        return mapper.toResponse(repository.save(user));
    }

    private User find(String identitySubject) {
        return repository.findByIdentitySubject(identitySubject)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found"));
    }
}
