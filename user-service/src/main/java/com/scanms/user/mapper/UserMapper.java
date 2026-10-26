package com.scanms.user.mapper;

import com.scanms.user.constant.RoleName;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "roles", expression = "java(toRoleNames(user.getRoles()))")
    UserResponse toResponse(User user);

    default Set<RoleName> toRoleNames(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        return roles.stream().map(Role::getName).collect(Collectors.toUnmodifiableSet());
    }
}
