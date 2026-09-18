package com.scanms.user.mapper;

import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getIdentitySubject(), user.getEmail(), user.getFullName(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
