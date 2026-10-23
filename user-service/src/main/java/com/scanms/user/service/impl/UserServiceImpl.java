package com.scanms.user.service.impl;

import com.scanms.user.constant.RoleName;
import com.scanms.user.constant.UserStatus;
import com.scanms.user.dto.PageResponse;
import com.scanms.user.dto.request.UpdateUserProfileRequest;
import com.scanms.user.dto.request.UpdateUserStatusRequest;
import com.scanms.user.dto.response.UserResponse;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.mapper.UserMapper;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.KeycloakAdminClient;
import com.scanms.user.service.UserService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final UserMapper mapper;

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
                    try {
                        names.add(RoleName.valueOf(name));
                    } catch (IllegalArgumentException ignored) { /* Keycloak built-in roles are not business roles. */ }
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
        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.phoneNumber() != null) user.setPhoneNumber(request.phoneNumber());
        if (request.dateOfBirth() != null) user.setDateOfBirth(request.dateOfBirth());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.address() != null) user.setAddress(request.address());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.bio() != null) user.setBio(request.bio());
        return mapper.toResponse(repository.save(user));
    }

    @Transactional(readOnly = true)
    @Override
    public PageResponse<UserResponse> getAllUsers(int page, int size, String search, RoleName role, UserStatus status) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);

        Specification<User> spec = buildAdminSearchSpec(search, role, status);
        Page<User> result = repository.findAll(spec, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    @Override
    public UserResponse getUserById(String userId) {
        return mapper.toResponse(findById(userId));
    }

    @Override
    public UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request) {
        User user = findById(userId);

        boolean isAdminAccount = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN);
        if (isAdminAccount) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không thể thay đổi trạng thái của tài khoản Quản trị viên (Admin)");
        }

        user.setStatus(request.status());
        return mapper.toResponse(repository.save(user));
    }

    private Specification<User> buildAdminSearchSpec(String search, RoleName role, UserStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(search)) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("fullName")), like)
                ));
            }

            if (role != null) {
                Join<User, Role> rolesJoin = root.join("roles");
                predicates.add(cb.equal(rolesJoin.get("name"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (query != null) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Tim user theo identitySubject (sub cua JWT). Neu chua co ban ghi local DB
     * (vi du: tai khoan duoc tao truc tiep tren Keycloak Admin Console, khong qua
     * luong /auth/register), tu dong tao ("auto-provision") ban ghi local dua tren
     * thong tin va role THUC TE cua tai khoan do tren Keycloak - khong gan cung
     * mot role USER cho tat ca.
     */
    private User find(String identitySubject) {
        return repository.findByIdentitySubject(identitySubject)
                .orElseGet(() -> autoProvisionFromKeycloak(identitySubject));
    }

    private User findById(String userId) {
        return repository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found"));
    }

    private User autoProvisionFromKeycloak(String identitySubject) {
        KeycloakAdminClient.KeycloakUserInfo kcUser;
        List<String> realmRoleNames;
        try {
            kcUser = keycloakAdminClient.getUserInfo(identitySubject);
            realmRoleNames = keycloakAdminClient.getUserRealmRoleNames(identitySubject);
        } catch (Exception e) {
            log.error("Khong the lay thong tin user tu Keycloak cho identitySubject={}", identitySubject, e);
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found");
        }

        if (kcUser == null) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found");
        }

        // Chi lay nhung role ten hop le trong enum RoleName (ADMIN, MANAGER, USER, KOL_CTV, SHOP_OWNER)
        // Bo qua cac role mac dinh cua Keycloak nhu offline_access, uma_authorization, default-roles-*
        List<RoleName> matchedRoles = new ArrayList<>();
        for (String roleName : realmRoleNames) {
            try {
                matchedRoles.add(RoleName.valueOf(roleName));
            } catch (IllegalArgumentException ignored) {
                // Khong phai role nghiep vu cua he thong -> bo qua
            }
        }
        if (matchedRoles.isEmpty()) {
            matchedRoles.add(RoleName.USER);
        }

        Set<Role> roles = roleRepository.findAllByNameIn(matchedRoles);

        String fullName = ((kcUser.lastName() != null ? kcUser.lastName() : "") + " "
                + (kcUser.firstName() != null ? kcUser.firstName() : "")).trim();
        if (fullName.isEmpty()) {
            fullName = kcUser.username();
        }

        User newUser = User.builder()
                .identitySubject(identitySubject)
                .username(kcUser.username())
                .email(kcUser.email())
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .roles(roles)
                .build();

        try {
            return repository.save(newUser);
        } catch (Exception e) {
            // Truong hop dua: 2 request cung luc deu auto-provision -> doc lai ban ghi da duoc tao
            log.warn("Auto-provision user that bai, thu doc lai ban ghi da ton tai cho identitySubject={}", identitySubject, e);
            return repository.findByIdentitySubject(identitySubject)
                    .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User profile not found"));
        }
    }
}
