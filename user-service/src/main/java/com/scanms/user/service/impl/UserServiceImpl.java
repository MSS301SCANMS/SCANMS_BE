package com.scanms.user.service.impl;

import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.mapper.UserMapper;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.entity.Role;
import com.scanms.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository repository;
    private final UserMapper mapper;
    private final RoleRepository roleRepository;

    // Jwt is verified by the resource server. Only signed realm roles can
    // provision privileges; no role or identity is accepted from a request body.
    public UserResponse synchronizeCurrentUser(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        User user = repository.findByIdentitySubject(subject).orElseGet(() -> {
            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Identity provider must supply an email claim");
            }
            String name = jwt.getClaimAsString("name");
            if (name == null || name.isBlank()) name = jwt.getClaimAsString("preferred_username");
            return User.builder().identitySubject(subject).email(email)
                    .fullName(name == null || name.isBlank() ? email : name)
                    .status(UserStatus.ACTIVE).build();
        });
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.FORBIDDEN, "User account is not active");
        }
        Set<RoleName> names = EnumSet.of(RoleName.USER);
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> realm && realm.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role instanceof String name) {
                    try { names.add(RoleName.valueOf(name)); }
                    catch (IllegalArgumentException ignored) { /* Keycloak built-in roles are not business roles. */ }
                }
            }
        }
        Set<Role> roles = roleRepository.findAllByNameIn(names);
        if (roles.size() != names.size()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Business roles are not initialized");
        }
        Set<RoleName> existing = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        if (user.getUserId() == null || !existing.equals(names)) {
            user.setRoles(roles);
            user = repository.saveAndFlush(user);
        }
        return mapper.toResponse(user);
    }

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
