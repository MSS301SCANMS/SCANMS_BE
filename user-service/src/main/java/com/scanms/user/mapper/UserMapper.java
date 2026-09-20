package com.scanms.user.mapper;

import com.scanms.user.constant.RoleName;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
    public UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getIdentitySubject(), user.getEmail(), user.getFullName(),
                user.getStatus(), toRoleNames(user.getRoles()), user.getCreatedAt(), user.getUpdatedAt());
    }

    private Set<RoleName> toRoleNames(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }
}
